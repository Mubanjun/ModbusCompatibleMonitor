/** 采集端口查询条件 */
export interface PortQuery extends PageQuery {
  description?: string
  portType?: string
  enabled?: boolean
}

/** 采集端口信息 */
export interface PortResp {
  id: string
  name: string
  portType: string
  portName: string
  baudRate?: number
  dataBits?: number
  stopBits?: number
  parity?: number
  flowControl?: number
  deviceLimit: number
  enabled: boolean
  description?: string
  sort: number
  createTime: string
  updateTime?: string
}

/** 采集端口创建或修改参数 */
export interface PortReq {
  name: string
  portType: string
  portName: string
  baudRate?: number
  dataBits?: number
  stopBits?: number
  parity?: number
  flowControl?: number
  deviceLimit: number
  enabled: boolean
  description?: string
  sort?: number
}

/** 采集设备查询条件 */
export interface DeviceQuery extends PageQuery {
  description?: string
  portId?: string
  protocol?: string
  groupId?: string
  groupName?: string
  enabled?: boolean
}

/** 采集设备信息 */
export interface DeviceResp {
  id: string
  portId: string
  name: string
  slaveAddr: number
  protocol: string
  model?: string
  groupId?: string
  groupName?: string
  location?: string
  longitude?: number
  latitude?: number
  elevation?: number
  address?: string
  intervalMs?: number
  timeoutMs: number
  sortNo: number
  enabled: boolean
  description?: string
  createTime: string
  updateTime?: string
}

/** 采集设备创建或修改参数 */
export interface DeviceReq {
  portId: string
  name: string
  slaveAddr: number
  protocol?: string
  model?: string
  groupId?: string
  groupName?: string
  location?: string
  longitude?: number
  latitude?: number
  elevation?: number
  address?: string
  intervalMs?: number
  timeoutMs?: number
  sortNo?: number
  enabled: boolean
  description?: string
}

/** 通道配置查询条件 */
export interface ChannelQuery extends PageQuery {
  description?: string
  deviceId?: string
  channelNo?: number
  analogNo?: number
  elementType?: string
  enabled?: boolean
}

/** 通道配置信息 */
export interface ChannelResp {
  id: string
  deviceId: string
  channelNo: number
  analogNo: number
  elementType?: string
  elementName?: string
  unit?: string
  dataType: string
  encoding: string
  regAddr: number
  coefficient: number
  offsetValue: number
  minValue?: number
  maxValue?: number
  enabled: boolean
  description?: string
  createTime: string
  updateTime?: string
}

/** 通道配置创建或修改参数 */
export interface ChannelReq {
  deviceId: string
  channelNo: number
  analogNo: number
  elementType?: string
  rangeLabel?: string
  elementName?: string
  unit?: string
  dataType?: string
  encoding?: string
  regAddr?: number
  coefficient?: number
  offsetValue?: number
  minValue?: number
  maxValue?: number
  enabled: boolean
  description?: string
}
/** 要素量程档位 */
export interface ElementRangeItem {
  model: string
  label: string
  min: number
  max: number
  encoding: string
  encodingDescription: string
  coefficientA: number
  resolution: number
}

/** 要素及量程字典项 */
export interface ElementTypeItem {
  code: string
  name: string
  unit: string
  ranges: ElementRangeItem[]
}
/** 设备模板查询条件 */
export interface DeviceTemplateQuery extends PageQuery {
  description?: string
  elementType?: string
  enabled?: boolean
}

/** 设备模板信息 */
export interface DeviceTemplateResp {
  id: string
  name: string
  model?: string
  elementType?: string
  rangeLabel?: string
  unit?: string
  dataType: string
  encoding: string
  coefficient: number
  offsetValue: number
  minValue?: number
  maxValue?: number
  isSystem?: boolean
  enabled: boolean
  description?: string
  sort: number
  createTime: string
  updateTime?: string
}

/** 设备模板创建或修改参数 */
export interface DeviceTemplateReq {
  name: string
  model?: string
  elementType?: string
  rangeLabel?: string
  unit?: string
  dataType?: string
  encoding?: string
  coefficient?: number
  offsetValue?: number
  minValue?: number
  maxValue?: number
  enabled: boolean
  description?: string
  sort?: number
}
/** 报警规则查询条件 */
export interface AlarmRuleQuery extends PageQuery {
  description?: string
  deviceId?: string
  portId?: string
  ruleType?: string
  elementType?: string
  enabled?: boolean
}

/** 报警规则信息 */
export interface AlarmRuleResp {
  id: string
  name: string
  ruleType: string
  deviceId?: string
  portId?: string
  channelNo?: number
  analogNo?: number
  elementType?: string
  upperLimit?: number
  lowerLimit?: number
  deviation?: number
  rateLimit?: number
  switchState?: number
  durationS: number
  suppressWindowS: number
  notifyMethods?: string
  enabled: boolean
  description?: string
  createTime: string
  updateTime?: string
}

/** 报警规则创建或修改参数 */
export interface AlarmRuleReq {
  name: string
  ruleType: string
  deviceId?: string
  portId?: string
  channelNo?: number
  analogNo?: number
  elementType?: string
  upperLimit?: number
  lowerLimit?: number
  deviation?: number
  rateLimit?: number
  switchState?: number
  durationS?: number
  suppressWindowS?: number
  notifyMethods?: string
  enabled: boolean
  description?: string
}
/** 上报目标查询条件 */
export interface ReportTargetQuery extends PageQuery {
  description?: string
  targetType?: string
  dbType?: string
  enabled?: boolean
}

/** 上报目标信息 */
export interface ReportTargetResp {
  id: string
  name: string
  targetType: string
  dbType?: string
  configJson: string
  formatType: string
  mqttQos?: number
  mqttRetain?: boolean
  retryCount: number
  retryIntervalMs: number
  timeoutMs: number
  enabled: boolean
  description?: string
  sort: number
  createTime: string
  updateTime?: string
}

/** 上报目标创建或修改参数 */
export interface ReportTargetReq {
  name: string
  targetType: string
  dbType?: string
  configJson: string
  formatType?: string
  mqttQos?: number
  mqttRetain?: boolean
  retryCount?: number
  retryIntervalMs?: number
  timeoutMs?: number
  enabled: boolean
  description?: string
  sort?: number
}

/** 上报模板查询条件 */
export interface ReportTemplateQuery extends PageQuery {
  description?: string
  formatType?: string
  enabled?: boolean
}

/** 上报模板信息 */
export interface ReportTemplateResp {
  id: string
  name: string
  code: string
  formatType: string
  content: string
  isSystem?: boolean
  enabled: boolean
  description?: string
  createTime: string
  updateTime?: string
}

/** 上报模板创建或修改参数 */
export interface ReportTemplateReq {
  name: string
  code: string
  formatType: string
  content: string
  enabled: boolean
  description?: string
}
/** 上报规则查询条件 */
export interface ReportRuleQuery extends PageQuery {
  description?: string
  targetId?: string
  templateId?: string
  portId?: string
  deviceId?: string
  enabled?: boolean
}

/** 上报规则信息 */
export interface ReportRuleResp {
  id: string
  name: string
  targetId: string
  templateId?: string
  portId?: string
  deviceId?: string
  intervalMs: number
  enabled: boolean
  description?: string
  createTime: string
  updateTime?: string
}

/** 上报规则创建或修改参数 */
export interface ReportRuleReq {
  name: string
  targetId?: string
  templateId?: string
  portId?: string
  deviceId?: string
  intervalMs?: number
  enabled: boolean
  description?: string
}

/** 采集顺序查询条件 */
export interface CollectOrderQuery extends PageQuery {
  portId?: string
  deviceId?: string
  enabled?: boolean
}

/** 采集顺序信息 */
export interface CollectOrderResp {
  id: string
  portId: string
  deviceId: string
  sortNo: number
  enabled: boolean
  createTime: string
  updateTime?: string
}

/** 采集顺序创建或修改参数 */
export interface CollectOrderReq {
  portId?: string
  deviceId?: string
  sortNo?: number
  enabled: boolean
}
/** 同步任务查询条件 */
export interface SyncTaskQuery extends PageQuery {
  description?: string
  targetId?: string
  syncMode?: string
  taskStatus?: string
  enabled?: boolean
}

/** 同步任务信息 */
export interface SyncTaskResp {
  id: string
  name: string
  targetId: string
  syncMode: string
  conflictPolicy: string
  tableNames?: string
  taskStatus: string
  lastSyncTime?: string
  totalCount?: number
  successCount?: number
  failCount?: number
  errorMsg?: string
  enabled: boolean
  description?: string
  createTime: string
  updateTime?: string
}

/** 同步任务创建或修改参数（仅配置项） */
export interface SyncTaskReq {
  name: string
  targetId?: string
  syncMode: string
  conflictPolicy: string
  tableNames?: string
  enabled: boolean
  description?: string
}
/** 业务系统配置查询条件 */
export interface SysConfigQuery extends PageQuery {
  description?: string
  category?: string
  code?: string
}

/** 业务系统配置信息 */
export interface SysConfigResp {
  id: string
  category: string
  name: string
  code: string
  value?: string
  defaultValue?: string
  description?: string
  createTime: string
  updateTime?: string
}

/** 业务系统配置创建或修改参数 */
export interface SysConfigReq {
  category: string
  name: string
  code: string
  value?: string
  defaultValue?: string
  description?: string
}
/** AlarmRecord 查询条件 */
export interface AlarmRecordQuery extends PageQuery {
  description?: string
  deviceId?: string
  nodeId?: number
  alarmType?: string
  handlingFlag?: number
}

/** AlarmRecord 信息 */
export interface AlarmRecordResp {
  id: string
  deviceName: string
  deviceAddr?: number
  nodeId?: number
  analogNo?: number
  elementName: string
  alarmType: string
  alarmMessage: string
  dataValue?: number
  unit: string
  alarmRange: string
  recordTime: string
  handlingFlag?: number
  handlingMethod: string
  handlingUser: string
  handlingTime: string
  createTime: string
}

/** AlarmNotify 查询条件 */
export interface AlarmNotifyQuery extends PageQuery {
  description?: string
  recordId?: string
  notifyType?: string
  sendStatus?: number
}

/** AlarmNotify 信息 */
export interface AlarmNotifyResp {
  id: string
  recordId: string
  notifyType: string
  target: string
  content: string
  sendStatus?: number
  retryCount?: number
  sendTime: string
  errorMsg: string
  createTime: string
}

/** SyncConflict 查询条件 */
export interface SyncConflictQuery extends PageQuery {
  description?: string
  taskId?: string
  tableName?: string
  policy?: string
}

/** SyncConflict 信息 */
export interface SyncConflictResp {
  id: string
  taskId: string
  tableName: string
  recordId: string
  sourceTime: string
  targetTime: string
  keptSource?: boolean
  policy: string
  detail: string
  createTime: string
}

/** SyncWatermark 查询条件 */
export interface SyncWatermarkQuery extends PageQuery {
  description?: string
  targetId?: string
  tableName?: string
}

/** SyncWatermark 信息 */
export interface SyncWatermarkResp {
  id: string
  targetId: string
  tableName: string
  lastRecordTime: string
  lastId: string
  createTime: string
}

/** WatchdogLog 查询条件 */
export interface WatchdogLogQuery extends PageQuery {
  description?: string
  processName?: string
  eventType?: string
}

/** WatchdogLog 信息 */
export interface WatchdogLogResp {
  id: string
  processName: string
  eventType: string
  eventTime: string
  detail: string
  restartCount?: number
  createTime: string
}

/** 采集参数 */
export interface AcquisitionSettingsResp {
  intervalMs: number
  timeoutMs: number
  retries: number
  pollBudgetMs: number
  failureThreshold: number
  circuitOpenMs: number
  maxBackoffMs: number
  nightMultiplier: number
  nightStartHour: number
  nightEndHour: number
}

/** 设备运行状态 */
export interface DeviceStatusResp {
  id: string
  name: string
  slaveAddr: number
  protocol: string
  status: string
  statusLabel: string
  enabled: boolean
  paused: boolean
  intervalMs: number
  channelCount: number
  lastPollTime?: number
  lastSuccessTime?: number
  pollCount: number
  successCount: number
  timeoutCount: number
  errorCount: number
  successRate: number
  averageDurationMs: number
  circuitState: string
  consecutiveFailures: number
  tripCount: number
  budgetExceeded: boolean
  lastError?: string
}

/** 端口运行状态 */
export interface PortStatusResp {
  id: string
  name: string
  portType: string
  portName: string
  connected: boolean
  paused: boolean
  busy: boolean
  deviceCount: number
  lastError?: string
  devices: DeviceStatusResp[]
}

/** 采集内核运行状态 */
export interface AcquisitionStatusResp {
  running: boolean
  paused: boolean
  startedAt?: number
  uptimeMs?: number
  lastReloadTime?: number
  roundCount: number
  devicePollCount: number
  sampleCount: number
  traceEnabled: boolean
  traceDroppedCount: number
  settings: AcquisitionSettingsResp
  lastError?: string
  ports: PortStatusResp[]
}

/** 模拟量测试结果 */
export interface AnalogTestResp {
  analogNo: number
  elementName?: string
  rawValue: number
  engineeringValue: number
  unit?: string
}

/** 通道测试明细 */
export interface ChannelTestResp {
  channelNo: number
  regAddr: number
  registers: number[]
  samples: AnalogTestResp[]
  errorMsg?: string
}

/** 信令帧 */
export interface SignalFrameResp {
  sequence: number
  timeMillis: number
  deviceId?: string
  deviceName?: string
  deviceAddr?: number
  direction: string
  frame: string
  frameLength: number
  parsed?: string
  durationMs?: number
  success: boolean
  errorMsg?: string
}

/** 设备通讯测试结果 */
export interface DeviceTestResp {
  deviceId: string
  deviceName?: string
  slaveAddr?: number
  protocol?: string
  portName?: string
  success: boolean
  durationMs?: number
  errorMsg?: string
  channels: ChannelTestResp[]
  frames: SignalFrameResp[]
}

/** SignalLog 查询条件 */
export interface SignalLogQuery extends PageQuery {
  description?: string
  portId?: string
  deviceId?: string
  direction?: string
  success?: boolean
  startTime?: string
  endTime?: string
}

/** SignalLog 信息 */
export interface SignalLogResp {
  id: string
  portId?: string
  portName?: string
  deviceId?: string
  deviceAddr?: number
  direction: string
  frame?: string
  frameLength?: number
  parsed?: string
  durationMs?: number
  success: boolean
  errorMsg?: string
  traceTime: string
  createTime: string
}

/** 历史数据查询条件 */
export interface DataQuery extends PageQuery {
  description?: string
  deviceId?: string
  deviceAddr?: number
  nodeId?: number
  analogNo?: number
  elementType?: string
  alarmFlag?: number
  startTime?: string
  endTime?: string
  recalculate?: boolean
  granularity?: string
}

/** 历史数据 */
export interface DataResp {
  id: string
  deviceId?: string
  deviceName?: string
  deviceAddr?: number
  nodeId?: number
  analogNo?: number
  elementType?: string
  elementName?: string
  dataValue?: number
  rawValue?: number
  unit?: string
  alarmFlag?: number
  recordTime: string
  sourceId?: string
  recalculated?: boolean
}

/** 历史数据聚合结果 */
export interface DataAggregateResp {
  bucket: string
  avgValue?: number
  minValue?: number
  maxValue?: number
  sampleCount?: number
}

/** 通道实时值查询条件 */
export interface DataLatestQuery extends PageQuery {
  description?: string
  deviceId?: string
  portId?: string
  nodeId?: number
  analogNo?: number
  elementType?: string
  deviceStatus?: string
  quality?: string
  overRange?: boolean
}

/** 通道实时值 */
export interface DataLatestResp {
  id: string
  deviceId?: string
  deviceName?: string
  deviceAddr?: number
  portId?: string
  nodeId?: number
  analogNo?: number
  elementType?: string
  elementName?: string
  dataValue?: number
  rawValue?: number
  unit?: string
  deviceStatus?: string
  quality?: string
  overRange?: boolean
  collectTime: string
}

/** 上报死信查询条件（FR-RPT-05） */
export interface ReportDeadLetterQuery extends PageQuery {
  description?: string
  targetId?: string
  ruleId?: string
  targetType?: string
  state?: string
}

/** 上报死信 */
export interface ReportDeadLetterResp {
  id: string
  targetId?: string
  ruleId?: string
  targetName?: string
  ruleName?: string
  targetType?: string
  formatType?: string
  payload?: string
  itemCount?: number
  retryCount?: number
  state?: string
  errorMsg?: string
  firstFailTime?: string
  replayTime?: string
  replayResult?: string
  createTime?: string
}

/** 同步任务进度 */
export interface SyncProgressResp {
  taskId: string
  taskName?: string
  status: string
  syncMode?: string
  conflictPolicy?: string
  currentTable?: string
  totalCount?: number
  successCount?: number
  conflictCount?: number
  skippedCount?: number
  failCount?: number
  durationMs?: number
  errorMsg?: string
}

/** 本地暂存与落地状态 */
export interface StagingStatusResp {
  pendingCount?: number
  inFlightCount?: number
  backlog?: number
  oldestCollectTime?: number
  newestCollectTime?: number
  appendedCount?: number
  flushedCount?: number
  droppedCount?: number
  fileSizeBytes?: number
  queuedCount?: number
  queuedDroppedCount?: number
  uploadedBatches?: number
  uploadedRows?: number
  failedBatches?: number
  purgedHistoryRows?: number
  bufferMaxRows?: number
  retainDays?: number
  flushIntervalMs?: number
  batchSize?: number
  databasePath?: string
  lastError?: string
  lastErrorTime?: number
}

/** 单路继电器状态 */
export interface RelayItem {
  relayNo: number
  register: number
  closed: boolean
  label: string
}

/** 继电器状态 */
export interface RelayStatusResp {
  deviceId: string
  deviceName?: string
  deviceAddr?: number
  portName?: string
  online?: boolean
  relayRegBase?: number
  relayCount?: number
  registers?: number[]
  relays?: RelayItem[]
  errorMsg?: string
}

/** 继电器操作结果 */
export interface RelayControlResp {
  deviceId: string
  deviceName?: string
  relayNo?: number
  requestValue?: string
  previousValue?: string
  readbackValue?: string
  readbackOk?: boolean
  durationMs?: number
  success?: boolean
  message?: string
  operateTime?: string
  logId?: string
  relays?: RelayItem[]
}

/** 继电器操作日志查询条件 */
export interface RelayLogQuery extends PageQuery {
  description?: string
  deviceId?: string
  relayNo?: number
  action?: string
  resultStatus?: string
  startTime?: string
  endTime?: string
}

/** 继电器操作日志 */
export interface RelayLogResp {
  id: string
  deviceId?: string
  deviceName?: string
  deviceAddr?: number
  portId?: string
  relayNo?: number
  action: string
  requestValue?: string
  previousValue?: string
  readbackValue?: string
  readbackOk?: boolean
  durationMs?: number
  resultStatus: string
  errorMsg?: string
  operateTime: string
  operatorName?: string
}

/** 日志级别（FR-LOG-01） */
export interface LogLevelResp {
  logger: string
  level: string
  managed: boolean
}

/** 历史数据修正结果（FR-WEB-08） */
export interface DataCorrectResp {
  id: string
  deviceName: string
  nodeId: number
  analogNo: number
  recordTime: string
  beforeValue: number
  afterValue: number
  beforeRawValue: number
  afterRawValue: number
  latestRefreshed: boolean
  message: string
}

/** 历史数据批量补算结果（FR-WEB-08） */
export interface DataRecalculateResp {
  dryRun: boolean
  scanned: number
  updated: number
  unchanged: number
  skipped: number
  failed: number
  durationMs: number
  errors: string[]
}

/** 迁移向导请求（FR-IO-06、FR-MIG） */
export interface MigrationReq {
  host?: string
  port?: number
  database?: string
  username?: string
  password?: string
  table?: string
  portId?: string
  protocol?: string
  nameColumn?: string
  addrColumn?: string
  startTime?: string
  endTime?: string
  dryRun?: boolean
  mode?: string
}

/** 源库探测结果（FR-IO-06） */
export interface MigrationDetectResp {
  connected: boolean
  database: string
  tableCount: number
  dr03Tables: string[]
  deviceTables: string[]
  tables: { name: string, rows: number, dr03: boolean, deviceTable: boolean }[]
  errorMsg: string
}

/** 迁移导入结果（FR-IO-06、FR-MIG-03/04） */
export interface MigrationImportResp {
  type: string
  dryRun: boolean
  scanned: number
  imported: number
  skipped: number
  failed: number
  durationMs: number
  errors: ImportRowErrorResp[]
}

/** 一致性校验抽样（FR-MIG-06） */
export interface MigrationSampleResp {
  deviceName: string
  nodeId: number
  analogNo: number
  recordTime: string
  sourceValue: number
  localValue: number
  consistent: boolean
}

/** 一致性校验报告（FR-MIG-06） */
export interface MigrationVerifyResp {
  checkedAt: string
  startTime: string
  endTime: string
  sourceRows: number
  localRows: number
  sampled: number
  matched: number
  mismatched: number
  consistent: boolean
  conclusion: string
  samples: MigrationSampleResp[]
}

/** 自检项（FR-WD-06） */
export interface SelfCheckItemResp {
  name: string
  ok: boolean
  detail: string
}

/** 看门狗重启事件（FR-WD-03/05） */
export interface RestartEventResp {
  time: string
  eventType: string
  reason: string
  restartCount: number
  pid: number
}

/** 看门狗状态（FR-WD-02/05/06） */
export interface WatchdogStatusResp {
  processName: string
  heartbeatFile: string
  watchdogLog: string
  lastHeartbeat: string
  heartbeatAgeSeconds?: number
  heartbeatMessage?: string
  heartbeatStale: boolean
  heartbeatTimeoutSeconds: number
  selfCheck: SelfCheckItemResp[]
  restarts: RestartEventResp[]
  restartCountLastHour: number
  restartLimitPerHour: number
  restartLimitExceeded: boolean
  logTail: string[]
}

/** 导入预览（FR-IO-01） */
export interface ImportPreviewResp {
  type: string
  headers: string[]
  headersValid: boolean
  missingHeaders: string[]
  total: number
  sampleRows: string[]
}

/** 导入行错误（FR-IO-01） */
export interface ImportRowErrorResp {
  row: number
  message: string
}

/** 导入报告（FR-IO-01） */
export interface ImportReportResp {
  type: string
  dryRun: boolean
  total: number
  success: number
  skipped: number
  failed: number
  durationMs: number
  errors: ImportRowErrorResp[]
}

/** 备份包（FR-IO-03） */
export interface BackupFileResp {
  name: string
  size: number
  createdAt: string
  encrypted: boolean
}

/** 备份校验结果（FR-IO-03） */
export interface BackupVerifyResp {
  name: string
  encrypted: boolean
  entries: number
  checked: number
  mismatched: string[]
  valid: boolean
}

/** 备份恢复结果（FR-IO-04） */
export interface BackupRestoreResp {
  name: string
  override: boolean
  preBackup: string
  tables: string[]
  statements: number
  notes: string[]
}

/** 日志文件（FR-LOG-03） */
export interface LogFileResp {
  path: string
  name: string
  module: string
  size: number
  sizeLabel: string
  lastModified: string
}

/** 单设备时间偏差（NFR-REL-06） */
export interface TimeSyncDeviceItem {
  deviceId: string
  deviceName?: string
  deviceAddr?: number
  portName?: string
  protocol?: string
  supported?: boolean
  online?: boolean
  deviceTime?: string
  offsetSeconds?: number
  withinTolerance?: boolean
  message?: string
}

/** 校时状态与设备时间偏差（NFR-REL-06） */
export interface TimeSyncStatusResp {
  serverTime?: string
  writeEnabled?: boolean
  autoEnabled?: boolean
  autoCron?: string
  toleranceSeconds?: number
  registerBase?: number
  lastRunTime?: string
  lastRunMessage?: string
  devices?: TimeSyncDeviceItem[]
}

/** 设备校时结果（NFR-REL-06） */
export interface TimeSyncResultResp {
  deviceId: string
  deviceName?: string
  deviceAddr?: number
  portName?: string
  protocol?: string
  action?: string
  serverTime?: string
  deviceTimeBefore?: string
  offsetSecondsBefore?: number
  writeSkipped?: boolean
  readbackTime?: string
  readbackOffsetSeconds?: number
  readbackOk?: boolean
  resultStatus?: string
  message?: string
  durationMs?: number
}

/** 批量校时请求（NFR-REL-06） */
export interface TimeSyncBatchReq {
  deviceIds?: string[]
  /** 校时方式（ALL：全部下发；TOLERANT：仅偏差超出容差的设备下发） */
  mode?: 'ALL' | 'TOLERANT'
}

/** 设备分组树节点 */
export interface DeviceGroupNode {
  id: string
  parentId: string
  name: string
  path: string
  depth: number
  sortNo: number
  directDeviceCount: number
  subtreeDeviceCount: number
  builtin: boolean
  remark?: string
  children: DeviceGroupNode[]
}

/** 设备分组创建/修改参数 */
export interface DeviceGroupReq {
  name: string
  parentId?: string
  sortNo?: number
  remark?: string
}

/** 传感器对象查询条件 */
export interface SensorQuery extends PageQuery {
  description?: string
  groupId?: string
  deviceId?: string
  elementCode?: string
  enabled?: boolean
}

/** 传感器对象 */
export interface SensorResp {
  id: string
  name: string
  groupId: string
  portId: string
  deviceId?: string
  channelNo?: number
  analogNo?: number
  elementCode: string
  unit?: string
  rangeMin?: number
  rangeMax?: number
  coefficient: number
  offsetValue: number
  precision?: number
  templateId?: string
  enabled: boolean
  createTime: string
  updateTime?: string
}

/** 传感器对象创建/修改参数 */
export interface SensorReq {
  name: string
  groupId?: string
  portId: string
  deviceId?: string
  channelNo?: number
  analogNo?: number
  elementCode: string
  unit?: string
  rangeMin?: number
  rangeMax?: number
  coefficient?: number
  offsetValue?: number
  precision?: number
  templateId?: string
  enabled: boolean
}

/** 设备位置更新参数 */
export interface DeviceLocationReq {
  deviceId?: string
  location?: string
  longitude?: number
  latitude?: number
  elevation?: number
  address?: string
}

/** 地图运行期配置 */
export interface DeviceMapConfig {
  enabled: boolean
  key: string
  securityCode: string
  coordinateSystem: 'WGS84'
  renderCoordinateSystem: 'GCJ02'
}
