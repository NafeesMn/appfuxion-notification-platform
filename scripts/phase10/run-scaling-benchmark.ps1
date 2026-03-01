param(
    [int]$Jobs = 4000,
    [int]$Partitions = 128,
    [int]$Batch = 100,
    [int]$WorkMicros = 1000
)

$ErrorActionPreference = "Stop"

Write-Host "Running Phase 10 scaling benchmark harness..."
Write-Host "jobs=$Jobs partitions=$Partitions batch=$Batch workMicros=$WorkMicros"

.\mvnw.cmd `
  "-Dtest=Phase10ScalingHarnessTest" `
  "-Drun.phase10.benchmark=true" `
  "-Dphase10.jobs=$Jobs" `
  "-Dphase10.partitions=$Partitions" `
  "-Dphase10.batch=$Batch" `
  "-Dphase10.workMicros=$WorkMicros" `
  test
