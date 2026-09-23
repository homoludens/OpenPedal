PHASE 2 — ADD BLE HEART RATE MONITOR

The existing Android app already connects to my YESOUL indoor bike
using Bluetooth FTMS and displays live bike data.

Do not redesign or refactor the existing working bike functionality.

Add support for a SECOND simultaneous BLE device: a heart-rate monitor.

In my case this is a smartwatch exposing the standard Bluetooth
Heart Rate Service. It has already been tested successfully with
nRF Connect.

Observed BLE service:

Heart Rate Service:
UUID 0x180D
0000180d-0000-1000-8000-00805f9b34fb

Heart Rate Measurement:
UUID 0x2A37
00002a37-0000-1000-8000-00805f9b34fb

Properties:
NOTIFY

CCCD:
UUID 0x2902
00002902-0000-1000-8000-00805f9b34fb

Example received value:
Heart Rate: 105 bpm

GOAL

Allow the app to connect simultaneously to:

1. YESOUL bike
   FTMS 0x1826 / Indoor Bike Data 0x2AD2

2. BLE heart-rate monitor/watch
   Heart Rate Service 0x180D / Measurement 0x2A37

Keep the implementation as simple as possible.

HEART RATE CONNECTION

Add a "Heart rate" section to the existing screen.

When no HR device is connected:

Heart rate
-- bpm
[ CONNECT HR ]

When pressed, scan for BLE devices advertising service 0x180D.

Show a very simple list of discovered HR devices:

Heart rate devices

Instinct 2 Solar
D5:B4:4E:28:D1:C6

[Cancel]

The device name/address above is only an example.
Do NOT hardcode it.

When the user taps a device:
- stop scanning
- connect with BluetoothGatt
- discover services
- find service 0x180D
- find characteristic 0x2A37
- enable notifications
- enable its 0x2902 CCCD
- receive Heart Rate Measurement notifications

The bike and HR monitor must remain connected simultaneously.

HEART RATE PARSER

Correctly implement the standard Bluetooth Heart Rate Measurement
characteristic.

Read the flags byte.

Heart rate may be encoded as:
- UINT8
or
- UINT16

depending on bit 0 of the flags.

Do not assume it is always one byte.

For now we only need:

data class HeartRateData(
    val bpm: Int? = null
)

Ignore RR intervals and energy-expended data for now.

UI

Add heart rate alongside the main live measurements.

For example:

--------------------------------
YESOUL Bike       ● Connected

             127 W
              POWER

     97 rpm          36.8 km/h
     CADENCE         SPEED

              105
              BPM ♥
--------------------------------

Distance          1.87 km
Resistance        35
Calories          25 kcal
Average power     80 W
Time              03:56
--------------------------------

Keep this UI simple.

ARCHITECTURE

Do not introduce a complex BLE framework or new architecture.

Add approximately:

ble/
    YesoulBike.kt
    HeartRateMonitor.kt
    IndoorBikeDataParser.kt
    HeartRateParser.kt

HeartRateMonitor should expose something like:

StateFlow<ConnectionState>
StateFlow<HeartRateData>

Keep separate BluetoothGatt connections for:
- bike
- heart-rate monitor

Do not disconnect the bike when connecting the HR monitor.

RECONNECTION

Keep it simple.

If HR disconnects:
- bike should continue working
- show "-- bpm"
- show Connect HR again

If bike disconnects:
- HR should continue working

No background service is needed yet.

DEBUGGING

Log:
- discovered HR devices
- HR connection state
- discovered HR services
- raw 0x2A37 packets as hex
- parsed BPM

IMPORTANT

Do not add:
- workout history
- database
- accounts
- cloud
- graphs
- Strava
- complicated device management
- background services

Keep Phase 2 small.

SUCCESS CRITERIA:

I can open the app, connect to my YESOUL bike, connect separately
to my smartwatch/HR monitor, start cycling, and see:

Power:       127 W
Cadence:      97 rpm
Speed:        36.8 km/h
Heart rate:  105 bpm

all updating live at the same time.
