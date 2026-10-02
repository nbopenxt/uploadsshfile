package com.openxt.uploadsshfile.importexport.merger;

/**
 * 单个合并器的统计结果。
 */
public class MergeCounts {
    private int added;
    private int updated;
    private int skipped;

    public MergeCounts() {
        this.added = 0;
        this.updated = 0;
        this.skipped = 0;
    }

    public int getAdded() { return added; }
    public void setAdded(int added) { this.added = added; }
    public void incrementAdded() { this.added++; }

    public int getUpdated() { return updated; }
    public void setUpdated(int updated) { this.updated = updated; }
    public void incrementUpdated() { this.updated++; }

    public int getSkipped() { return skipped; }
    public void setSkipped(int skipped) { this.skipped = skipped; }
    public void incrementSkipped() { this.skipped++; }
}
