package pt.uminho.v2x;

import java.util.concurrent.ThreadLocalRandom;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.AdHocModuleConfiguration;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.geo.GeoCircle;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;

public class VeiculoApp extends AbstractApplication<VehicleOperatingSystem> implements VehicleApplication, CommunicationApplication {

    private static final double VEHICLE_RETRANSMISSION_RADIUS_M = 500.0;
    private static final double ADOPTION_PROBABILITY = 1.00; // 100% dos veículos aderem às recomendações (comportamento determinístico)
    private static final long SPEED_COMMAND_INTERVAL_NS = 2_000_000_000L;
    
    // --- NOVOS PARÂMETROS PARA PREVENIR BROADCAST STORMS ---
    private static final double FORWARDING_PROBABILITY = 0.35; // Apenas 35% agem como retransmissores (Gossip Protocol)
    private static final long COOLDOWN_RETRANSMISSAO_NS = 2_000_000_000L; // 2 segundos de silêncio rádio após retransmitir

    private int lastSequenceProcessed = -1;
    private int lastSequenceForwarded = -1;
    private long lastForwardTime = 0; // Registo do tempo da última retransmissão

    @Override
    public void onStartup() {
        getLog().info("Veiculo {} a ligar radio ITS-G5.", getOs().getId());

        try {
            getOs().getAdHocModule().enable(
                new AdHocModuleConfiguration()
                    .addRadio()
                    .distance(VEHICLE_RETRANSMISSION_RADIUS_M)
                    .channel(AdHocChannel.CCH)
                    .create()
            );
        } catch (Exception e) {
            getLog().error("Erro ao ativar radio do veiculo {}: {}", getOs().getId(), e.getMessage());
        }
    }

    @Override
    public boolean canProcessEvent() {
        return false;
    }

    @Override
    public void processEvent(Event event) {
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        if (receivedV2xMessage == null || receivedV2xMessage.getMessage() == null) {
            return;
        }

        if (!(receivedV2xMessage.getMessage() instanceof AvisoObraMessage)) {
            return;
        }

        AvisoObraMessage msg = (AvisoObraMessage) receivedV2xMessage.getMessage();

        // Bloqueio de pacotes já processados (Evita loops infinitos)
        if (msg.getSequenceNumber() <= lastSequenceProcessed) {
            return;
        }

        lastSequenceProcessed = msg.getSequenceNumber();

        getLog().info(
            "Veiculo {} recebeu aviso seq={} recomendacao={} m/s estado={}",
            getOs().getId(),
            msg.getSequenceNumber(),
            String.format("%.2f", msg.getRecommendedSpeedMps()),
            msg.getTrafficState()
        );

        aplicarRecomendacaoVelocidade(msg);

        // A nova lógica de decisão de retransmissão
        if (deveRetransmitir(msg)) {
            retransmitirAviso(msg);
            lastSequenceForwarded = msg.getSequenceNumber();
            lastForwardTime = getOs().getSimulationTime(); // Atualiza o tempo do Cooldown
        }
    }

    private void aplicarRecomendacaoVelocidade(AvisoObraMessage msg) {
        double draw = ThreadLocalRandom.current().nextDouble(0.0, 1.0);

        if (draw <= ADOPTION_PROBABILITY) {
            getOs().changeSpeedWithInterval(msg.getRecommendedSpeedMps(), SPEED_COMMAND_INTERVAL_NS);
            getLog().info(
                "Veiculo {} ADERIU a recomendacao de {} m/s",
                getOs().getId(),
                String.format("%.2f", msg.getRecommendedSpeedMps())
            );
        } else {
            getLog().info("Veiculo {} IGNOROU a recomendacao por comportamento não deterministico.", getOs().getId());
        }
    }

    private boolean deveRetransmitir(AvisoObraMessage msg) {
        // 1. Já retransmitimos esta sequência recentemente?
        if (msg.getSequenceNumber() <= lastSequenceForwarded) {
            return false;
        }

        // 2. Cooldown: Proteção contra saturação espectral (Rate Limiting)
        long currentTime = getOs().getSimulationTime();
        if ((currentTime - lastForwardTime) < COOLDOWN_RETRANSMISSAO_NS) {
            return false;
        }

        // 3. Mitigação do Flooding Cego (Gossip Routing)
        // Reduz drasticamente as colisões de pacotes na rede V2V
        double draw = ThreadLocalRandom.current().nextDouble(0.0, 1.0);
        return draw <= FORWARDING_PROBABILITY;
    }

    private void retransmitirAviso(AvisoObraMessage original) {
        try {
            GeoCircle areaV2V = new GeoCircle(getOs().getPosition(), VEHICLE_RETRANSMISSION_RADIUS_M);

            MessageRouting routing = getOs().getAdHocModule().createMessageRouting()
                .broadcast()
                .geographical(areaV2V)
                .channel(AdHocChannel.CCH)
                .build();

            AvisoObraMessage retransmissao = new AvisoObraMessage(
                routing,
                original.getEventType(),
                original.getSequenceNumber(),
                original.getRecommendedSpeedMps(),
                original.getTrafficState(),
                original.getCreationTimeNs(),
                original.getSourceUnitId()
            );

            getOs().getAdHocModule().sendV2xMessage(retransmissao);

            getLog().info(
                "Veiculo {} retransmitiu aviso seq={} por multi-hop V2V.",
                getOs().getId(),
                original.getSequenceNumber()
            );
        } catch (Exception e) {
            getLog().error("Erro na retransmissão do veiculo {}: {}", getOs().getId(), e.getMessage());
        }
    }

    @Override
    public void onVehicleUpdated(VehicleData previousVehicleData, VehicleData updatedVehicleData) {}

    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement acknowledgement) {}

    @Override
    public void onCamBuilding(CamBuilder camBuilder) {}

    @Override
    public void onMessageTransmitted(V2xMessageTransmission transmission) {}

    @Override
    public void onShutdown() {}
}