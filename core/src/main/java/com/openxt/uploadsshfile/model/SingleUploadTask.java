package com.openxt.uploadsshfile.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 单任务上传的持久化配置快照（1.0.8 / D-37）。
 *
 * <p>语义：UploadDialog <b>关窗时</b>（OK / Cancel / ✕ 统一收口）把当次界面的
 * 文件清单与服务器/路径/命令组/时机选择整体覆盖保存到 {@code singleUploadTask} 单槽；
 * CLI {@code run <任务ID>} 命中 {@code singleUploadTaskId} 后即以本快照为唯一文件来源
 * （D-38，{@code --file} 旗标作废，片段仅带任务 ID）。
 *
 * <p>与既有记忆的分工（并存不互斥，参照 {@code singleUploadTaskId} 的设计注）：
 * 任务 ID 仍只存于 {@link UnifiedPluginConfig#getSingleUploadTaskId()}（查重/清理机制不动），
 * 本实体不复制 ID 防两处失同步；{@code lastSuccessful*} 四项语义不变——那是
 * "上次<b>成功执行</b>的记忆"，本快照是"上次<b>关窗保存</b>的配置"，回显优先快照、回落旧四项。
 * Gson 老配置缺字段时本槽为 null＝从未关窗保存过（无格式校验，R16 风格自愈）。
 */
public class SingleUploadTask {

    /** 文件/目录绝对路径清单（字段名对齐 BatchSubTask.filePaths 的实名口径，D-32 JSON 实名教训） */
    private List<String> filePaths;

    /** 服务器配置 id（不透明字符串，随删除联动清 null） */
    private String serverId;

    /** 上传路径配置 id（可 null＝未选/仅命令） */
    private String pathId;

    /** 命令组配置 id（可 null） */
    private String commandConfigId;

    /** 执行时机（ExecuteTiming.name()，可 null） */
    private String timing;

    public SingleUploadTask() {
        this.filePaths = new ArrayList<>();
    }

    public List<String> getFilePaths() {
        if (filePaths == null) filePaths = new ArrayList<>();
        return filePaths;
    }

    public void setFilePaths(List<String> filePaths) {
        this.filePaths = filePaths;
    }

    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }

    public String getPathId() { return pathId; }
    public void setPathId(String pathId) { this.pathId = pathId; }

    public String getCommandConfigId() { return commandConfigId; }
    public void setCommandConfigId(String commandConfigId) { this.commandConfigId = commandConfigId; }

    public String getTiming() { return timing; }
    public void setTiming(String timing) { this.timing = timing; }
}
