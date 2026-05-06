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

    private static final long CONTROL_PERIOD_NS = 1_000_000_000L; 
    private static final long CAM_FRESHNESS_NS = 5_000_000_000L;  

    private static final double RSU_BROADCAST_RADIUS_M = 500.0;
    private static final double CRITICAL_ZONE_RADIUS_M = 500.0; 

    private static final double RECOMMENDED_SPEED_CONGESTED_MPS = 10.0; 
    private static final double RECOMMENDED_SPEED_FREEFLOW_MPS = 30.0;  

    // VARIÁVEIS DE CONTROLO DE FILA (O diagnóstico correto do teu colega)
    private static final double LENTO_THRESHOLD_MPS = 5.0; // Considera preso se < 18 km/h
    private static final int MIN_CARROS_LENTOS_ALERTA = 5; 

    // HISTERESE TEMPORAL (A prevenção do Efeito Ping-Pong)
    private static final long COOLDOWN_MUDANCA_ESTADO_NS = 30_000_000_000L; // 30 segundos
    private long tempoUltimaMudancaNs = 0;

    private final Map<String, CamSnapshot> camByVehicle = new HashMap<>();
    private TrafficState currentState = TrafficState.FREE_FLOW;
    private double currentRecommendationMps = RECOMMENDED_SPEED_FREEFLOW_MPS;
    private int currentSequenceNumber = 0;

    private enum TrafficState { FREE_FLOW, CONGESTED }

    private static class CamSnapshot {
        private long lastUpdateTimeNs;
        private double speedMps;
    }

    @Override
    public void onStartup() {
        try {
            getOs().getAdHocModule().enable(
                new AdHocModuleConfiguration().addRadio().distance(RSU_BROADCAST_RADIUS_M).channel(AdHocChannel.CCH).create()
            );
        } catch (Exception e) {}
        getOs().getEventManager().addEvent(new Event(getOs().getSimulationTime() + CONTROL_PERIOD_NS, this));
    }

    @Override public boolean canProcessEvent() { return true; }

    @Override
    public void processEvent(Event event) {
        limparCamsAntigas();
        recalcularEstadoTrafego();
        enviarAvisoAdaptativo();
        getOs().getEventManager().addEvent(new Event(getOs().getSimulationTime() + CONTROL_PERIOD_NS, this));
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        if (receivedV2xMessage != null && receivedV2xMessage.getMessage() instanceof Cam) {
            processarCam((Cam) receivedV2xMessage.getMessage());
        }
    }

    private void processarCam(Cam cam) {
        if (cam.getUnitID() == null || cam.getAwarenessData() == null) return;

        GeoPoint rsuPosition = getOs().getPosition();
        GeoPoint vehiclePosition = cam.getPosition();

        if (rsuPosition != null && vehiclePosition != null && rsuPosition.distanceTo(vehiclePosition) > CRITICAL_ZONE_RADIUS_M) {
            camByVehicle.remove(cam.getUnitID());
            return;
        }

        double speedMps = 0.0;
        try {
            if (cam.getAwarenessData() instanceof VehicleAwarenessData) {
                speedMps = ((VehicleAwarenessData) cam.getAwarenessData()).getSpeed();
            } else return;
        } catch (Exception e) { return; }

        CamSnapshot snapshot = new CamSnapshot();
        snapshot.speedMps = speedMps;
        snapshot.lastUpdateTimeNs = getOs().getSimulationTime();
        camByVehicle.put(cam.getUnitID(), snapshot);
    }

    private void limparCamsAntigas() {
        long now = getOs().getSimulationTime();
        Iterator<Map.Entry<String, CamSnapshot>> iterator = camByVehicle.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue().lastUpdateTimeNs > CAM_FRESHNESS_NS) iterator.remove();
        }
    }

    private void recalcularEstadoTrafego() {
        if (camByVehicle.isEmpty()) return;

        int carrosLentos = 0;
        for (CamSnapshot snapshot : camByVehicle.values()) {
            if (snapshot.speedMps < LENTO_THRESHOLD_MPS) carrosLentos++;
        }

        long now = getOs().getSimulationTime();

        if (currentState == TrafficState.FREE_FLOW) {
            if (carrosLentos >= MIN_CARROS_LENTOS_ALERTA && (now - tempoUltimaMudancaNs > COOLDOWN_MUDANCA_ESTADO_NS)) {
                currentState = TrafficState.CONGESTED;
                currentRecommendationMps = RECOMMENDED_SPEED_CONGESTED_MPS;
                tempoUltimaMudancaNs = now;
                getLog().warn("ONDA DE CHOQUE: {} carros lentos detetados. A ativar limite V2X (10 m/s).", carrosLentos);
            }
        } else {
            if (carrosLentos == 0 && (now - tempoUltimaMudancaNs > COOLDOWN_MUDANCA_ESTADO_NS)) {
                currentState = TrafficState.FREE_FLOW;
                currentRecommendationMps = RECOMMENDED_SPEED_FREEFLOW_MPS;
                tempoUltimaMudancaNs = now;
                getLog().info("VIA LIVRE: Fila dissipada. A cancelar restrições V2X.");
            }
        }
    }

    private void enviarAvisoAdaptativo() {
        try {
            GeoCircle areaComunicacao = new GeoCircle(getOs().getPosition(), RSU_BROADCAST_RADIUS_M);
            MessageRouting routing = getOs().getAdHocModule().createMessageRouting().broadcast().geographical(areaComunicacao).channel(AdHocChannel.CCH).build();

            AvisoObraMessage msg = new AvisoObraMessage(
                routing, "WORKZONE_SPEED", currentSequenceNumber++, currentRecommendationMps,
                currentState.name(), getOs().getSimulationTime(), getOs().getId(), 3, getOs().getPosition()
            );
            getOs().getAdHocModule().sendV2xMessage(msg);
        } catch (Exception e) {}
    }

    @Override public void onAcknowledgementReceived(ReceivedAcknowledgement a) {}
    @Override public void onCamBuilding(CamBuilder c) {}
    @Override public void onMessageTransmitted(V2xMessageTransmission t) {}
    @Override public void onShutdown() {}
}