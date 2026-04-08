package dev.ignis.valkyrienjs.feature.blocklimit;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BlockLimitEntry {
    @JsonProperty("blockId")
    private String blockId;

    @JsonProperty("maxCount")
    private int maxCount;

    @JsonProperty("currentCount")
    private int currentCount;

    // 无参构造函数用于 Jackson 反序列化
    @SuppressWarnings("unused")
    private BlockLimitEntry() {
    }

    public BlockLimitEntry(String blockId, int maxCount) {
        this(blockId, maxCount, 0);
    }

    public BlockLimitEntry(String blockId, int maxCount, int currentCount) {
        this.blockId = blockId;
        this.maxCount = maxCount;
        this.currentCount = currentCount;
    }

    public String getBlockId() {
        return blockId;
    }

    public int getMaxCount() {
        return maxCount;
    }

    public int getCurrentCount() {
        return currentCount;
    }

    public void setCurrentCount(int count) {
        this.currentCount = count;
    }

    public void increment() {
        this.currentCount++;
    }

    public void decrement() {
        this.currentCount--;
    }

    public boolean canPlace() {
        return currentCount < maxCount;
    }
}
