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
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;

public class VeiculoApp extends AbstractApplication<VehicleOperatingSystem> implements VehicleApplication, CommunicationApplication {

    private static final double VEHICLE_RETRANSMISSION_RADIUS_M = 500.0;
    private static final double ADOPTION_PROBABILITY = 1.00;
    private static final long SPEED_COMMAND_INTERVAL_NS = 2_000_000_000L;
    
    // REDEFINIÇÃO DO FILTRO ESPACIAL (State-of-the-Art em VANETs)
    private static final double FORWARDING_PROBABILITY = 0.30; // Podemos subir ligeiramente porque o filtro de distância é agressivo
    private static final long COOLDOWN_RETRANSMISSAO_NS = 2_000_000_000L; 
    private static final double MIN_FORWARDING_DISTANCE_M = 350.0; // SÓ retransmite se estiver a >350m de quem enviou!

    private int lastSequenceProcessed = -1;
    private int lastSequenceForwarded = -1;
    private long lastForwardTime = 0;

    @Override
    public void onStartup() {
        try {
            getOs().getAdHocModule().enable(
                new AdHocModuleConfiguration().addRadio().distance(VEHICLE_RETRANSMISSION_RADIUS_M).channel(AdHocChannel.CCH).create()
            );
        } catch (Exception e) {}
    }

    @Override public boolean canProcessEvent() { return false; }
    @Override public void processEvent(Event event) {}

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        if (receivedV2xMessage == null || !(receivedV2xMessage.getMessage() instanceof AvisoObraMessage)) return;

        AvisoObraMessage msg = (AvisoObraMessage) receivedV2xMessage.getMessage();

        if (msg.getSequenceNumber() <= lastSequenceProcessed) return;
        lastSequenceProcessed = msg.getSequenceNumber();

        aplicarRecomendacaoVelocidade(msg);

        // Bloqueio 1: TTL Expirado
        if (msg.getHopLimit() <= 0) return;

        if (deveRetransmitir(msg)) {
            retransmitirAviso(msg);
            lastSequenceForwarded = msg.getSequenceNumber();
            lastForwardTime = getOs().getSimulationTime();
        }
    }

    private void aplicarRecomendacaoVelocidade(AvisoObraMessage msg) {
        if (ThreadLocalRandom.current().nextDouble(0.0, 1.0) <= ADOPTION_PROBABILITY) {
            getOs().changeSpeedWithInterval(msg.getRecommendedSpeedMps(), SPEED_COMMAND_INTERVAL_NS);
        }
    }

    private boolean deveRetransmitir(AvisoObraMessage msg) {
        if (msg.getSequenceNumber() <= lastSequenceForwarded) return false;
        if ((getOs().getSimulationTime() - lastForwardTime) < COOLDOWN_RETRANSMISSAO_NS) return false;
        
        // Bloqueio 2: Filtro Espacial (A grande novidade)
        GeoPoint myPosition = getOs().getPosition();
        GeoPoint senderPosition = msg.getSenderPosition();
        
        if (myPosition != null && senderPosition != null) {
            double distanceToSender = myPosition.distanceTo(senderPosition);
            if (distanceToSender < MIN_FORWARDING_DISTANCE_M) {
                // Muito perto de quem enviou. Deixa outro veículo mais longe tratar do assunto!
                getLog().info("Veiculo {} abdicou de retransmitir (distância {:.1f}m < {:.1f}m) - Prevenindo Flooding.", 
                              getOs().getId(), distanceToSender, MIN_FORWARDING_DISTANCE_M);
                return false;
            }
        }

        return ThreadLocalRandom.current().nextDouble(0.0, 1.0) <= FORWARDING_PROBABILITY;
    }

    private void retransmitirAviso(AvisoObraMessage original) {
        try {
            GeoCircle areaV2V = new GeoCircle(getOs().getPosition(), VEHICLE_RETRANSMISSION_RADIUS_M);
            MessageRouting routing = getOs().getAdHocModule().createMessageRouting().broadcast().geographical(areaV2V).channel(AdHocChannel.CCH).build();

            int novosSaltosRestantes = original.getHopLimit() - 1;

            AvisoObraMessage retransmissao = new AvisoObraMessage(
                routing, original.getEventType(), original.getSequenceNumber(),
                original.getRecommendedSpeedMps(), original.getTrafficState(),
                original.getCreationTimeNs(), original.getSourceUnitId(),
                novosSaltosRestantes,
                getOs().getPosition() // O veículo injeta a sua própria posição como nova origem!
            );

            getOs().getAdHocModule().sendV2xMessage(retransmissao);
            getLog().info("Veiculo {} retransmitiu (Hop Limit: {})", getOs().getId(), novosSaltosRestantes);
        } catch (Exception e) {}
    }

    @Override public void onVehicleUpdated(VehicleData p, VehicleData u) {}
    @Override public void onAcknowledgementReceived(ReceivedAcknowledgement a) {}
    @Override public void onCamBuilding(CamBuilder c) {}
    @Override public void onMessageTransmitted(V2xMessageTransmission t) {}
    @Override public void onShutdown() {}
}