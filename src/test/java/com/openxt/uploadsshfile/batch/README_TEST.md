# 批处理进度模拟测试

## 测试文件清单

1. **BatchProgressSimulator.java** - 模拟进度事件生成器
   - 模拟4个子任务（2个大文件 + 2个多文件文件夹）
   - 生成详细的进度事件序列
   - 输出到 `build/test-logs/batch_progress_simulation.log`

2. **BatchProgressValidator.java** - 进度计算验证器
   - 验证进度计算的准确性
   - 检测进度跳变问题
   - 生成验证报告到 `build/test-logs/batch_progress_validation.log`

3. **BatchProgressSimulationTest.java** - JUnit 4 测试类
   - 包含9个测试方法
   - 验证任务创建、字节数计算、进度连续性等

4. **BatchProgressSimulationTestRunner.java** - 测试运行器
   - 可以直接运行的主类
   - 依次执行所有测试方法

## 运行方式

### 方式1：使用 Gradle 任务（推荐）

```bash
./gradlew runBatchProgressSimulationTest
```

或在 IDEA 中：
1. 打开 Gradle 面板
2. 找到 `uploadsshfile` -> `verification` -> `runBatchProgressSimulationTest`
3. 双击运行

### 方式2：直接运行 Java 主类

在 IDEA 中：
1. 打开 `BatchProgressSimulationTestRunner.java`
2. 右键点击 `main` 方法
3. 选择 "Run 'BatchProgressSimulationTestRunner.main()'"

### 方式3：使用 JUnit 运行器

```bash
./gradlew test --tests "com.openxt.uploadsshfile.batch.BatchProgressSimulationTest"
```

## 测试场景

按照测试方案设计，包含4个子任务：

| 子任务 | 类型 | 文件配置 | 预计大小 |
|--------|------|----------|----------|
| 1 | 单文件 | 1个300MB文件 | 300 MB |
| 2 | 多文件文件夹 | 1个文件夹，200个1-10MB文件 | ~1,100 MB |
| 3 | 单文件 | 1个300MB文件 | 300 MB |
| 4 | 多文件夹 | 2个文件夹，各200个1-10MB文件 | ~2,200 MB |

**总字节数**: ~3,900 MB

## 测试方法

1. **testTaskCreation** - 验证测试任务创建
2. **testTotalBytesCalculation** - 验证总字节数计算
3. **testFullSimulation** - 运行完整模拟并统计事件
4. **testProgressContinuity** - 验证进度计算连续性（关键）
5. **testSubTaskTransition** - 验证子任务切换时的进度处理
6. **testLargeFileUpload** - 验证大文件上传进度
7. **testSmallFilesBatchUpload** - 验证小文件批量上传
8. **testAllFilesCompletedSuccessfully** - 验证所有文件成功完成
9. **testLogGeneration** - 验证日志文件生成

## 输出文件

测试完成后会生成两个日志文件：

1. **build/test-logs/batch_progress_simulation.log**
   - 完整的模拟进度事件序列
   - 包含每个子任务、每个文件的上传进度

2. **build/test-logs/batch_progress_validation.log**
   - 验证报告
   - 包含通过/失败/警告的验证项
   - 详细的失败项说明

## 预期发现的问题

根据您提供的截图，测试应该能够检测到：

- ✅ 子任务切换时的进度跳变问题
- ✅ 总任务进度计算错误
- ✅ 子任务进度计算不准确

测试运行后，请检查验证报告中的失败项，这些将帮助定位具体的代码问题。

## 下一步

测试运行完成后：
1. 查看验证报告中的失败项
2. 根据失败信息定位 `BatchProgressDialog.java` 中的问题代码
3. 修复进度计算逻辑
4. 重新运行测试验证修复效果
