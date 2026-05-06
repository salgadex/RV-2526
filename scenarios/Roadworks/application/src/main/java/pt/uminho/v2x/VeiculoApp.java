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
    private static final double ADOPTION_PROBABILITY = 1.00; // Frota 100% cooperativa
    private static final long SPEED_COMMAND_INTERVAL_NS = 2_000_000_000L;
    
    // FILTRO ESPACIAL V2V (State-of-the-Art em VANETs para mitigar flooding cego)
    private static final double FORWARDING_PROBABILITY = 0.30; 
    private static final long COOLDOWN_RETRANSMISSAO_NS = 2_000_000_000L; 
    private static final double MIN_FORWARDING_DISTANCE_M = 350.0; 

    // AUTONOMIA TOPOLÓGICA 
    
    private static final String EDGE_APOS_OBRA = "edge_ida_3"; 
    
    // Variáveis de estado
    private int lastSequenceProcessed = -1;
    private int lastSequenceForwarded = -1;
    private long lastForwardTime = 0;
    private boolean restricoesLevantadas = false; // Memória interna do veículo

   @Override
    public void onStartup() {
        try {
            getOs().getAdHocModule().enable(
                new AdHocModuleConfiguration()
                    .addRadio()
                    .distance(VEHICLE_RETRANSMISSION_RADIUS_M)
                    .channel(AdHocChannel.CCH)
                    .create()
            );
            
            // ADICIONAR ESTA LINHA (Pseudo-DCC para mitigar saturação MAC)
          

        } catch (Exception e) {}
    }

    @Override public boolean canProcessEvent() { return false; }
    @Override public void processEvent(Event event) {}

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        if (receivedV2xMessage == null || !(receivedV2xMessage.getMessage() instanceof AvisoObraMessage)) return;

        AvisoObraMessage msg = (AvisoObraMessage) receivedV2xMessage.getMessage();

        // 1. Evita processamento em loop (Proteção MAC básica)
        if (msg.getSequenceNumber() <= lastSequenceProcessed) return;
        lastSequenceProcessed = msg.getSequenceNumber();

        // 2. Controla o comportamento físico (Desaceleração ou Libertação)
        aplicarRecomendacaoVelocidade(msg);

        // 3. Avalia retransmissão tática (Gossip Routing multi-hop)
        if (msg.getHopLimit() <= 0) return;

        if (deveRetransmitir(msg)) {
            retransmitirAviso(msg);
            lastSequenceForwarded = msg.getSequenceNumber();
            lastForwardTime = getOs().getSimulationTime();
        }
    }

    private void aplicarRecomendacaoVelocidade(AvisoObraMessage msg) {
        // GATILHO GEOGRÁFICO DE LIBERTAÇÃO ABSOLUTA
        if (getOs().getRoadPosition() != null) {
            String edgeAtual = getOs().getRoadPosition().getConnection().getId();
            
            // Se já chegou ao troço livre, ou se a memória indica que já passou a obra
            if (edgeAtual.equals(EDGE_APOS_OBRA) || restricoesLevantadas) {
                if (!restricoesLevantadas) { // Para não estar constantemente a enviar comandos ao SUMO
                    getOs().changeSpeedWithInterval(50.0, SPEED_COMMAND_INTERVAL_NS); // Retoma marcha máxima da via
                    getLog().info("Veiculo {} passou o gargalo (Edge: {}). Autonomia ativada, a retomar velocidade máxima.", getOs().getId(), edgeAtual);
                    restricoesLevantadas = true;
                }
                return; // Aborta imediatamente: o veículo recusa abrandar de novo!
            }
        }

        // LÓGICA DE ABRANDAMENTO (Apenas executada ANTES da obra)
        if (ThreadLocalRandom.current().nextDouble(0.0, 1.0) <= ADOPTION_PROBABILITY) {
            if ("CONGESTED".equals(msg.getTrafficState())) {
                getOs().changeSpeedWithInterval(msg.getRecommendedSpeedMps(), SPEED_COMMAND_INTERVAL_NS);
                getLog().info("Veiculo {} ADERIU ao aviso V2X. Abrandou para {} m/s", getOs().getId(), msg.getRecommendedSpeedMps());
            } else if ("FREE_FLOW".equals(msg.getTrafficState())) {
                getOs().changeSpeedWithInterval(50.0, SPEED_COMMAND_INTERVAL_NS); 
            }
        } else {
            getLog().info("Veículo {} IGNOROU a recomendação.", getOs().getId());
        }
    }

    private boolean deveRetransmitir(AvisoObraMessage msg) {
        if (msg.getSequenceNumber() <= lastSequenceForwarded) return false;
        if ((getOs().getSimulationTime() - lastForwardTime) < COOLDOWN_RETRANSMISSAO_NS) return false;
        
        // FILTRO ESPACIAL ATIVO (Prevenção massiva de Flooding Cego)
        GeoPoint myPosition = getOs().getPosition();
        GeoPoint senderPosition = msg.getSenderPosition();
        
        if (myPosition != null && senderPosition != null) {
            double distanceToSender = myPosition.distanceTo(senderPosition);
            if (distanceToSender < MIN_FORWARDING_DISTANCE_M) {
                getLog().info("Veiculo {} cancelou retransmissao V2V (esta a {:.1f}m do emissor original). Protecao de Flooding Ativa.", 
                              getOs().getId(), distanceToSender);
                return false;
            }
        }
        return ThreadLocalRandom.current().nextDouble(0.0, 1.0) <= FORWARDING_PROBABILITY;
    }

    private void retransmitirAviso(AvisoObraMessage original) {
        try {
            GeoCircle areaV2V = new GeoCircle(getOs().getPosition(), VEHICLE_RETRANSMISSION_RADIUS_M);
            MessageRouting routing = getOs().getAdHocModule().createMessageRouting().broadcast().geographical(areaV2V).channel(AdHocChannel.CCH).build();

            AvisoObraMessage retransmissao = new AvisoObraMessage(
                routing, original.getEventType(), original.getSequenceNumber(),
                original.getRecommendedSpeedMps(), original.getTrafficState(), 
                original.getCreationTimeNs(), original.getSourceUnitId(),
                original.getHopLimit() - 1,
                getOs().getPosition() 
            );

            getOs().getAdHocModule().sendV2xMessage(retransmissao);
            getLog().info("Veiculo {} retransmitiu o pacote V2X (Hop Limit: {})", getOs().getId(), original.getHopLimit() - 1);
        } catch (Exception e) {}
    }

    // ... (restantes overrides vazios) ...
    @Override public void onVehicleUpdated(VehicleData p, VehicleData u) {}
    @Override public void onAcknowledgementReceived(ReceivedAcknowledgement a) {}
    @Override public void onCamBuilding(CamBuilder c) {}
    @Override public void onMessageTransmitted(V2xMessageTransmission t) {}
    @Override public void onShutdown() {}
}