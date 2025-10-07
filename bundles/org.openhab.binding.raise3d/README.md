# Raise3D Binding

This binding integrates Raise3D 3D printers with openHAB, allowing you to monitor the status, progress, and various parameters of your 3D printer.

## Supported Things

This binding supports the following thing type:

- `printer` - A Raise3D 3D Printer (e.g., Raise3D N2Plus)

## Discovery

Automatic discovery is not supported.
You must manually configure your printer thing with the IP address and password.

## Thing Configuration

The printer thing requires the following configuration parameters:

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| ipAddress | text | yes | - | The IP address of the Raise3D printer |
| password | text | yes | - | The password for accessing the printer API |
| refreshInterval | integer | no | 30 | The refresh interval in seconds for polling the printer status (minimum 5 seconds) |

## Channels

### Job Information

| Channel ID | Item Type | Description |
|------------|-----------|-------------|
| fileName | String | Name of the file being printed |
| jobId | String | ID of the current print job |
| jobStatus | String | Status of the print job (e.g., "running") |
| printProgress | Number:Dimensionless | Progress of the current print job (0-100%) |
| printedLayer | Number | Number of layers printed |
| printedTime | Number:Time | Time elapsed for the print job |
| totalLayer | Number | Total number of layers in the print job |
| totalTime | Number:Time | Estimated total time for the print job |

### System Information

| Channel ID | Item Type | Description |
|------------|-----------|-------------|
| serialNumber | String | Serial number of the printer |
| apiVersion | String | Version of the printer API |
| battery | Number:Dimensionless | Battery level (%) |
| brightness | Number | Screen brightness level |
| dateTime | String | Date and time on the printer |
| firmwareVersion | String | Firmware version of the printer |
| language | String | Language setting of the printer |
| machineId | String | Machine ID of the printer |
| machineIp | String | IP address of the printer |
| machineName | String | Name of the printer |
| model | String | Model of the printer (e.g., "Raise3D N2Plus") |
| nozzlesNum | Number | Number of nozzles |
| storageAvailable | Number:DataAmount | Available storage space |
| update | String | Update information |
| version | String | Software version |

### Running Status

| Channel ID | Item Type | Description |
|------------|-----------|-------------|
| runningStatus | String | Current running status of the printer (e.g., "idle", "printing", "offline") |
| fanCurSpeed | Number | Current fan speed |
| fanTarSpeed | Number | Target fan speed |
| feedCurRate | Number | Current feed rate |
| feedTarRate | Number | Target feed rate |
| heatbedCurTemp | Number:Temperature | Current temperature of the heated bed |
| heatbedTarTemp | Number:Temperature | Target temperature of the heated bed |

## Special Behavior

- When the printer cannot be reached (network error, powered off, etc.), the `runningStatus` channel will be set to "offline"
- The binding automatically handles authentication and token management with the printer.

## Full Example

### Thing Configuration

```java
Thing raise3d:printer:myprinter [ ipAddress="192.168.1.123", password="yourpassword", refreshInterval=30 ]
```

### Items

```java
// Job Information
String Printer_FileName "File Name [%s]" { channel="raise3d:printer:myprinter:fileName" }
String Printer_JobStatus "Job Status [%s]" { channel="raise3d:printer:myprinter:jobStatus" }
Number:Dimensionless Printer_PrintProgress "Print Progress [%.0f %%]" { channel="raise3d:printer:myprinter:printProgress" }
Number Printer_PrintedLayer "Printed Layer [%d]" { channel="raise3d:printer:myprinter:printedLayer" }
Number Printer_TotalLayer "Total Layers [%d]" { channel="raise3d:printer:myprinter:totalLayer" }
Number:Time Printer_PrintedTime "Printed Time [%.0f %unit%]" { channel="raise3d:printer:myprinter:printedTime" }
Number:Time Printer_TotalTime "Total Time [%.0f %unit%]" { channel="raise3d:printer:myprinter:totalTime" }

// System Information
String Printer_MachineName "Machine Name [%s]" { channel="raise3d:printer:myprinter:machineName" }
String Printer_Model "Model [%s]" { channel="raise3d:printer:myprinter:model" }
String Printer_FirmwareVersion "Firmware [%s]" { channel="raise3d:printer:myprinter:firmwareVersion" }

// Running Status
String Printer_RunningStatus "Status [%s]" { channel="raise3d:printer:myprinter:runningStatus" }
Number:Temperature Printer_HeatbedTemp "Heatbed Temperature [%.1f °C]" { channel="raise3d:printer:myprinter:heatbedCurTemp" }
Number:Temperature Printer_HeatbedTarget "Heatbed Target [%.1f °C]" { channel="raise3d:printer:myprinter:heatbedTarTemp" }
```

### Sitemap

```perl
sitemap raise3d label="3D Printer" {
    Frame label="Print Job" {
        Text item=Printer_FileName
        Text item=Printer_JobStatus
        Text item=Printer_PrintProgress
        Text item=Printer_PrintedLayer
        Text item=Printer_TotalLayer
        Text item=Printer_PrintedTime
        Text item=Printer_TotalTime
    }
    Frame label="System" {
        Text item=Printer_MachineName
        Text item=Printer_Model
        Text item=Printer_FirmwareVersion
    }
    Frame label="Status" {
        Text item=Printer_RunningStatus
        Text item=Printer_HeatbedTemp
        Text item=Printer_HeatbedTarget
    }
}
```

## Notes

- This binding is read-only; it only monitors the printer and does not support sending commands.
- Ensure the password matches the one configured on your Raise3D printer.
