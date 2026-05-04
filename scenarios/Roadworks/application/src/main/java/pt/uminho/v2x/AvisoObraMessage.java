package pt.uminho.v2x;

import javax.annotation.Nonnull;

import org.eclipse.mosaic.lib.objects.v2x.EncodedPayload;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;

public class AvisoObraMessage extends V2xMessage {

    private static final long serialVersionUID = 1L;
    private static final long MIN_PAYLOAD_LENGTH_BYTES = 128L;

    private final String eventType;
    private final int sequenceNumber;
    private final double recommendedSpeedMps;
    private final String trafficState;
    private final long creationTimeNs;
    private final String sourceUnitId;

    public AvisoObraMessage(
            MessageRouting routing,
            String eventType,
            int sequenceNumber,
            double recommendedSpeedMps,
            String trafficState,
            long creationTimeNs,
            String sourceUnitId) {
        super(routing);
        this.eventType = eventType;
        this.sequenceNumber = sequenceNumber;
        this.recommendedSpeedMps = recommendedSpeedMps;
        this.trafficState = trafficState;
        this.creationTimeNs = creationTimeNs;
        this.sourceUnitId = sourceUnitId;
    }

    public String getEventType() {
        return eventType;
    }

    @Override
    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public double getRecommendedSpeedMps() {
        return recommendedSpeedMps;
    }

    public String getTrafficState() {
        return trafficState;
    }

    public long getCreationTimeNs() {
        return creationTimeNs;
    }

    public String getSourceUnitId() {
        return sourceUnitId;
    }

    @Override
    @Nonnull
    public EncodedPayload getPayload() {
        return new EncodedPayload(MIN_PAYLOAD_LENGTH_BYTES);
    }
}