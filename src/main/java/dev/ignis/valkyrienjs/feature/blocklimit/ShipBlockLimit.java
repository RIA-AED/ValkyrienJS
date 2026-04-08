package dev.ignis.valkyrienjs.feature.blocklimit;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.ServerShip;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE
)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShipBlockLimit {

    public static final String ATTACHMENT_ID = "valkyrienjs:block_limit";

    @JsonProperty("blockLimits")
    private Map<String, BlockLimitEntry> blockLimits = new HashMap<>();

    public Map<String, BlockLimitEntry> getBlockLimits() {
        return blockLimits;
    }

    public void setBlockLimits(Map<String, BlockLimitEntry> limits) {
        this.blockLimits = limits != null ? limits : new HashMap<>();
    }

    public static Optional<ShipBlockLimit> get(ServerShip ship) {
        if (ship instanceof LoadedServerShip loadedShip) {
            ShipBlockLimit attachment = loadedShip.getAttachment(ShipBlockLimit.class);
            return Optional.ofNullable(attachment);
        }
        return Optional.empty();
    }

    public static ShipBlockLimit getOrCreate(LoadedServerShip ship) {
        ShipBlockLimit attachment = ship.getAttachment(ShipBlockLimit.class);
        if (attachment == null) {
            attachment = new ShipBlockLimit();
            ship.setAttachment(ShipBlockLimit.class, attachment);
        }
        return attachment;
    }
}
