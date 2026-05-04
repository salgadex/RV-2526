package pt.uminho.v2x;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.AdHocModuleConfiguration;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.geo.GeoCircle;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.etsi.Cam;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.fed.application.app.api.os.RoadSideUnitOperatingSystem;
import org.eclipse.mosaic.lib.objects.v2x.etsi.cam.VehicleAwarenessData;

public class RsuObrasApp extends AbstractApplication<RoadSideUnitOperatingSystem> implements CommunicationApplication {

    private static final long CONTROL_PERIOD_NS = 1_000_000_000L; // 1 segundo
    private static final long CAM_FRESHNESS_NS = 5_000_000_000L;  // 5 segundos

    private static final double RSU_BROADCAST_RADIUS_M = 500.0;
    
    // --- Filtro Geográfico (Edge Computing) ---
    private static final double CRITICAL_ZONE_RADIUS_M = 500.0; 

    // --- Velocidades Recomendadas ---
    private static final double RECOMMENDED_SPEED_CONGESTED_MPS = 10.0; // 36 km/h para passar na obra
    private static final double RECOMMENDED_SPEED_FREEFLOW_MPS = 30.0;  // 108 km/h em marcha normal livre

    // --- Limiares de Histerese (Gatilhos Afinados) ---
    private static final double ENTER_CONGESTION_MEAN_SPEED_MPS = 24.5; 
    private static final double EXIT_CONGESTION_MEAN_SPEED_MPS = 27.5;  

    private static final int ENTER_CONGESTION_MIN_VEHICLES = 8;
    private static final int EXIT_CONGESTION_MAX_VEHICLES = 5;

    private final Map<String, CamSnapshot> camByVehicle = new HashMap<>();

    private TrafficState currentState = TrafficState.FREE_FLOW;
    private double currentRecommendationMps = RECOMMENDED_SPEED_FREEFLOW_MPS;
    private int currentSequenceNumber = 0;

    private enum TrafficState {
        FREE_FLOW,
        CONGESTED
    }

    private static class CamSnapshot {
        private long lastUpdateTimeNs;
        private double speedMps;
    }

    @Override
    public void onStartup() {
        getLog().info("RSU {} a inicializar controlo adaptativo com Agregacao Geografica.", getOs().getId());

        try {
            getOs().getAdHocModule().enable(
                new AdHocModuleConfiguration()
                    .addRadio()
                    .distance(RSU_BROADCAST_RADIUS_M)
                    .channel(AdHocChannel.CCH)
                    .create()
            );

            getLog().info("RSU {} com radio ITS-G5 ativo no canal CCH.", getOs().getId());
        } catch (Exception e) {
            getLog().error("Falha ao ativar modulo ad-hoc da RSU {}: {}", getOs().getId(), e.getMessage());
        }

        getOs().getEventManager().addEvent(
            new Event(getOs().getSimulationTime() + CONTROL_PERIOD_NS, this)
        );
    }

    @Override
    public boolean canProcessEvent() {
        return true;
    }

    @Override
    public void processEvent(Event event) {
        limparCamsAntigas();
        recalcularEstadoTrafego();
        enviarAvisoAdaptativo();

        getOs().getEventManager().addEvent(
            new Event(getOs().getSimulationTime() + CONTROL_PERIOD_NS, this)
        );
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        if (receivedV2xMessage == null || receivedV2xMessage.getMessage() == null) {
            return;
        }

        if (receivedV2xMessage.getMessage() instanceof Cam) {
            Cam cam = (Cam) receivedV2xMessage.getMessage();
            processarCam(cam);
        }
    }

    private void processarCam(Cam cam) {
        if (cam.getUnitID() == null || cam.getAwarenessData() == null) {
            return;
        }

        // 1. Filtro Geográfico (Ignorar veículos fluidos fora da zona de aproximação)
        GeoPoint rsuPosition = getOs().getPosition();
        GeoPoint vehiclePosition = cam.getPosition();

        if (rsuPosition != null && vehiclePosition != null) {
            double distanceToRsu = rsuPosition.distanceTo(vehiclePosition);
            if (distanceToRsu > CRITICAL_ZONE_RADIUS_M) {
                // Se o veículo saiu da zona crítica, removemos o seu histórico para não prender a média
                camByVehicle.remove(cam.getUnitID());
                return;
            }
        }

        // 2. Extração da Velocidade (Lógica Original Correta)
        double speedMps = 0.0;
        try {
            if (cam.getAwarenessData() instanceof VehicleAwarenessData) {
                VehicleAwarenessData vAd = (VehicleAwarenessData) cam.getAwarenessData();
                speedMps = vAd.getSpeed();
            } else {
                return;
            }
        } catch (Exception e) {
            getLog().warn("Falha ao ler a velocidade da CAM do nó: " + cam.getUnitID());
            return; 
        }

        // 3. Atualização do Snapshot
        CamSnapshot snapshot = new CamSnapshot();
        snapshot.speedMps = speedMps;
        snapshot.lastUpdateTimeNs = getOs().getSimulationTime();
        camByVehicle.put(cam.getUnitID(), snapshot);
    }

    private void limparCamsAntigas() {
        long now = getOs().getSimulationTime();
        Iterator<Map.Entry<String, CamSnapshot>> iterator = camByVehicle.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, CamSnapshot> entry = iterator.next();
            if (now - entry.getValue().lastUpdateTimeNs > CAM_FRESHNESS_NS) {
                iterator.remove();
            }
        }
    }

    private void recalcularEstadoTrafego() {
        int vehicleCount = camByVehicle.size();
        if (vehicleCount == 0) {
            return;
        }

        double sumSpeed = 0.0;
        for (CamSnapshot snapshot : camByVehicle.values()) {
            sumSpeed += snapshot.speedMps;
        }
        double meanSpeed = sumSpeed / vehicleCount;

        if (currentState == TrafficState.FREE_FLOW) {
            if (vehicleCount >= ENTER_CONGESTION_MIN_VEHICLES
                    && meanSpeed <= ENTER_CONGESTION_MEAN_SPEED_MPS) {
                currentState = TrafficState.CONGESTED;
                currentRecommendationMps = RECOMMENDED_SPEED_CONGESTED_MPS;
                getLog().warn("ONDA DE CHOQUE DETETADA! A mudar estado para CONGESTED.");
            }
        } else {
            if (vehicleCount <= EXIT_CONGESTION_MAX_VEHICLES
                    || meanSpeed >= EXIT_CONGESTION_MEAN_SPEED_MPS) {
                currentState = TrafficState.FREE_FLOW;
                currentRecommendationMps = RECOMMENDED_SPEED_FREEFLOW_MPS;
            }
        }

        getLog().info(
            "RSU {} estado={} veiculosNaROI={} velocidadeMediaROI={} m/s recomendacao={} m/s",
            getOs().getId(),
            currentState,
            vehicleCount,
            String.format("%.2f", meanSpeed),
            String.format("%.2f", currentRecommendationMps)
        );
    }

    private void enviarAvisoAdaptativo() {
        try {
            GeoCircle areaComunicacao = new GeoCircle(getOs().getPosition(), RSU_BROADCAST_RADIUS_M);

            // A RSU deve fazer broadcast geográfico direto. O multi-hop é responsabilidade dos veículos!
            MessageRouting routing = getOs().getAdHocModule().createMessageRouting()
                .broadcast()
                .geographical(areaComunicacao)
                .channel(AdHocChannel.CCH)
                .build();

            AvisoObraMessage msg = new AvisoObraMessage(
                routing,
                "WORKZONE_SPEED",
                currentSequenceNumber++,
                currentRecommendationMps,
                currentState.name(),
                getOs().getSimulationTime(),
                getOs().getId()
            );

            getOs().getAdHocModule().sendV2xMessage(msg);

        } catch (Exception e) {
            getLog().error("Erro no envio adaptativo da RSU {}: {}", getOs().getId(), e.getMessage());
        }
    }

    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement acknowledgement) {
    }

    @Override
    public void onCamBuilding(CamBuilder camBuilder) {
    }

    @Override
    public void onMessageTransmitted(V2xMessageTransmission transmission) {
    }

    @Override
    public void onShutdown() {
    }
}