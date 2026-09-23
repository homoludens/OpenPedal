Create a very simple native Android app for connecting to my YESOUL indoor exercise bike over Bluetooth Low Energy (BLE).

Use:
- Kotlin
- Jetpack Compose
- Native Android Bluetooth/BLE APIs
- StateFlow
- Material 3
- Minimum dependencies
- Keep architecture simple. Do NOT overengineer.

The bike exposes the standard Bluetooth Fitness Machine Service (FTMS).

Known BLE data from nRF Connect:

Device name:
YESOUL272412

Fitness Machine Service:
UUID 0x1826

Fitness Machine Feature:
UUID 0x2ACC
Properties: READ

Indoor Bike Data:
UUID 0x2AD2
Properties: NOTIFY

Client Characteristic Configuration:
UUID 0x2902

The 0x2AD2 characteristic is already confirmed working with notifications.

Example values observed:

Speed: 36.83 km/h
Cadence: 97 rpm
Total Distance: 1873 m
Resistance Level: 35
Instantaneous Power: 127 W
Average Power: 80 W
Total Energy: 25 kcal
Elapsed Time: 236 s

GOAL

Build the simplest possible app that:

1. Finds the YESOUL bike.
2. Connects to it.
3. Discovers FTMS service 0x1826.
4. Subscribes to notifications from Indoor Bike Data 0x2AD2.
5. Correctly parses FTMS Indoor Bike Data.
6. Displays the live measurements.

Do NOT implement bike control, workouts, accounts, databases, history,
charts, cloud synchronization, navigation, or other unnecessary features.

UI

Use ONE screen only.

Example:

--------------------------------
YESOUL Bike       ● Connected

             127 W
              POWER

     97 rpm          36.8 km/h
     CADENCE         SPEED

--------------------------------

Distance          1.87 km
Resistance        35
Calories          25 kcal
Average power     80 W
Time              03:56

--------------------------------

When disconnected:

YESOUL Bike       ● Disconnected

        [ CONNECT ]

While searching:

        Searching for bike...

Keep the UI clean, minimal and readable.

BLE

Use standard 128-bit Android UUID representations:

Fitness Machine Service:
00001826-0000-1000-8000-00805f9b34fb

Fitness Machine Feature:
00002acc-0000-1000-8000-00805f9b34fb

Indoor Bike Data:
00002ad2-0000-1000-8000-00805f9b34fb

CCCD:
00002902-0000-1000-8000-00805f9b34fb

Scan for BLE devices advertising FTMS service 0x1826.

Prefer a device whose name starts with "YESOUL".

Stop scanning once the bike is found and connect using BluetoothGatt.

After connection:
- discover services
- find service 0x1826
- find characteristic 0x2AD2
- enable notifications
- enable CCCD notifications

Support Android's current BLE APIs and permissions, including
BLUETOOTH_SCAN and BLUETOOTH_CONNECT where required.

Handle permission requests in the app.

FTMS PARSER

IMPORTANT:

Do NOT parse 0x2AD2 using fixed byte offsets.

Implement the Bluetooth SIG FTMS Indoor Bike Data format correctly.

The packet starts with a little-endian flags field.

Read the flags and advance through the packet only for fields that are
present.

Parse at least:

- instantaneous speed
- average speed
- instantaneous cadence
- average cadence
- total distance
- resistance level
- instantaneous power
- average power
- total energy
- energy per hour
- energy per minute
- heart rate
- elapsed time
- remaining time

Unknown or absent values should simply be null.

Be careful about:
- little-endian encoding
- signed vs unsigned fields
- FTMS scaling factors
- cadence scaling
- speed scaling

Create:

data class BikeData(
    val speedKmh: Double? = null,
    val averageSpeedKmh: Double? = null,
    val cadenceRpm: Double? = null,
    val averageCadenceRpm: Double? = null,
    val distanceMeters: Int? = null,
    val resistance: Double? = null,
    val powerWatts: Int? = null,
    val averagePowerWatts: Int? = null,
    val calories: Int? = null,
    val heartRate: Int? = null,
    val elapsedSeconds: Int? = null
)

ARCHITECTURE

Keep it deliberately small.

Something approximately like:

MainActivity.kt

ble/
    YesoulBike.kt
    IndoorBikeDataParser.kt

ui/
    BikeScreen.kt

Do not introduce Clean Architecture, dependency injection, Room,
Retrofit, multiple modules, repositories or unnecessary abstraction.

YesoulBike should expose something like:

StateFlow<ConnectionState>
StateFlow<BikeData>

The Compose UI observes those values.

RECONNECTION

Keep reconnection simple.

If the connection drops:
- show Disconnected
- expose the Connect button again

Don't build a complicated background BLE service yet.

DEBUGGING

Log:
- discovered device name/address
- connection state
- discovered services
- raw 0x2AD2 packet as hexadecimal
- parsed BikeData

This is important because I will test the implementation against the
real YESOUL bike and can provide raw packets if parsing needs adjustment.

DELIVERABLE

Produce a complete buildable Android Studio project.

Before finishing:
- compile the project
- fix compilation errors
- make sure required permissions are in AndroidManifest.xml
- make sure runtime Bluetooth permissions work
- verify the FTMS parser against the Bluetooth SIG Indoor Bike Data
  specification
- keep the implementation as small and understandable as possible

Do not add features that I didn't request.

The first milestone is simply:

Open app -> Connect -> pedal bike -> see speed, cadence, power,
resistance, distance, calories and elapsed time updating live.
