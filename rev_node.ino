#include <SPI.h>
#include <LoRa.h>
#include <LowPower.h>
#define TIMEZONE_OFFSET_HOURS -4 

// ------------------------ KONFIGURASI PIN ------------------------
const int hall1Pin = 3;
const int hall2Pin = 7;

// Timer untuk cetak periodik
unsigned long lastPrintMillis = 0;
const unsigned long printInterval = 10000; 

// ------------------------ STRUKTUR DATA --------------------------
struct PutaranData {
  char rumah[10];
  int16_t putaran;
  unsigned long timestamp; // waktu kirim (epoch millis sinkron)
};

PutaranData data;
volatile bool isHall1Triggered = false;
int pulseCount = 1;

// ------------------------ VARIABEL SINKRONISASI WAKTU --------------------------
unsigned long node_ms_rx = 0;
unsigned long gw_epoch_rx = 0;
long timeOffset = 0;  // theta = gateway_epoch - millis()

// Konstanta zona waktu (sesuaikan GMT+ berapa)
//const int TIMEZONE_OFFSET_HOURS = 0;

// ------------------------ FUNGSI UTILITAS --------------------------
uint64_t parseUint64(const String& str) {
  uint64_t value = 0;
  for (int i = 0; i < str.length(); i++) {
    char c = str.charAt(i);
    if (c >= '0' && c <= '9') {
      value = value * 10 + (c - '0');
    } else {
      break;
    }
  }
  return value;
}
void printFormattedTime(unsigned long long epochMs, const char* label) {
  // Ambil waktu sejak tengah malam (bukan total sejak 1970)
  unsigned long long msSinceMidnight = epochMs % 86400000ULL;  // 24 jam dalam ms

  unsigned int jam = msSinceMidnight / 3600000;
  unsigned int menit = (msSinceMidnight % 3600000) / 60000;
  unsigned int detik = (msSinceMidnight % 60000) / 1000;
  unsigned int ms = msSinceMidnight % 1000;

  Serial.print(label);
  if (jam < 10) Serial.print("0");
  Serial.print(jam); Serial.print(":");

  if (menit < 10) Serial.print("0");
  Serial.print(menit); Serial.print(":");

  if (detik < 10) Serial.print("0");
  Serial.print(detik); Serial.print(".");

  if (ms < 100) Serial.print("0");
  if (ms < 10) Serial.print("0");
  Serial.println(ms);
}


// ------------------------ SETUP --------------------------
void setup() {
  Serial.begin(115200);
  pinMode(hall1Pin, INPUT);
  pinMode(hall2Pin, INPUT);
  pinMode(13, OUTPUT);
  digitalWrite(13, LOW);

  attachInterrupt(digitalPinToInterrupt(hall1Pin), hall1Interrupt, FALLING);

  if (!LoRa.begin(433E6)) {
    Serial.println("LoRa tidak terhubung");
    while (1);
  }
  Serial.println("LoRa Initialized");
  LoRa.setTxPower(20); 
}

// ------------------------ LOOP --------------------------
void loop() {
  checkForSync();

  // Logika pembacaan sensor Hall
  int hall1Value = digitalRead(hall1Pin);
  if (hall1Value == 0) {
    Serial.println("Hall 1 Triggered");
    int on = 0;
    while (on == 0) {
      int hall2Value = digitalRead(hall2Pin);
      delay(300);
      Serial.print("Hall 2: ");
      Serial.println(hall2Value);

      if (hall2Value == 0) {
        pulseCount++;
        delay(2000);

        data.putaran = pulseCount;
        strcpy(data.rumah, "A_1");
        data.timestamp = millis() + timeOffset;

        sendData(data);
        on = 1;
      }
    }
    LoRa.sleep();
  }

  // Cetak waktu sinkron setiap 10 detik
  if (timeOffset != 0 && millis() - lastPrintMillis >= printInterval) {
    const long TIMEZONE_OFFSET_MS = -4L * 3600L * 1000L;
    unsigned long currentEpoch = millis() + timeOffset + TIMEZONE_OFFSET_MS;
    printFormattedTime(currentEpoch, "Waktu sekarang (sinkron): ");
    lastPrintMillis = millis();
  }
}

// ------------------------ ISR --------------------------
void hall1Interrupt() {
  isHall1Triggered = true;
}

// ------------------------ KIRIM DATA --------------------------
void sendData(PutaranData &data) {
  byte dataBuffer[sizeof(PutaranData)];
  memcpy(dataBuffer, &data, sizeof(PutaranData));

  LoRa.beginPacket();
  LoRa.write(dataBuffer, sizeof(PutaranData));
  LoRa.endPacket();

  Serial.print("Data dikirim: Rumah=");
  Serial.print(data.rumah);
  Serial.print(", Putaran=");
  Serial.print(data.putaran);
  Serial.print(", Timestamp=");
  Serial.println(data.timestamp);

  LoRa.sleep();
}

// ------------------------ SINKRONISASI --------------------------
void checkForSync() {
  int packetSize = LoRa.parsePacket();
  if (packetSize) {
    String incoming = "";
    while (LoRa.available()) {
      incoming += (char)LoRa.read();
    }

    if (incoming.startsWith("SYNC,")) {
      // Format: SYNC,<epochMillis>,<HH:MM:SS:MMM>
      int firstComma = incoming.indexOf(',', 5);
      if (firstComma == -1) return;

      String epochStr = incoming.substring(5, firstComma);
      String waktuStr = incoming.substring(firstComma + 1);

      // Hitung offset
      epochStr.trim();
      uint64_t gwEpoch = parseUint64(epochStr);
      gw_epoch_rx = gwEpoch;
      node_ms_rx = millis();
      timeOffset = gw_epoch_rx - node_ms_rx;

      Serial.print("Waktu sinkron dari gateway: ");
      Serial.println(waktuStr);
      Serial.print("Offset waktu (theta): ");
      Serial.println(timeOffset);

      // ✅ Terapkan kompensasi -4 jam
      long tzOffsetMs = TIMEZONE_OFFSET_HOURS * 3600L * 1000L;

      // Cetak waktu sinkron langsung
      unsigned long currentEpoch = millis() + timeOffset + tzOffsetMs;
      printFormattedTime(currentEpoch, "Waktu sinkron langsung: ");

      // Reset timer cetak periodik
      lastPrintMillis = millis();
      Serial.println();
    }
  }  
}
