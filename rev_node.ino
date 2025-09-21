#include <SPI.h>
#include <LoRa.h>
#include <LowPower.h>

// ------------------------ KONFIGURASI PIN ------------------------
const int hall1Pin = 3;
const int hall2Pin = 7;

// ------------------------ STRUKTUR DATA --------------------------
struct PutaranData {
  char rumah[10];
  int16_t putaran;
  unsigned long timestamp; // waktu kirim (epoch millis, dihitung)
};

PutaranData data;
volatile bool isHall1Triggered = false;
int pulseCount = 1;

// ------------------------ VARIABEL SINKRONISASI WAKTU --------------------------
unsigned long node_ms_rx = 0;
unsigned long gw_epoch_rx = 0;
long timeOffset = 0;  // theta = gateway_epoch - millis()

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
}

void loop() {
  checkForSync();

  int hall1Value = digitalRead(hall1Pin);
  if (hall1Value == 0) {
    Serial.print("Hall 1: ");
    Serial.println(hall1Value);
    int on = 0;

    while (on == 0) {
      int hall2Value = digitalRead(hall2Pin);
      Serial.print("Hall 2: ");
      delay(300);
      Serial.println(hall2Value);

      if (hall2Value == 0) {
        pulseCount++;
        delay(2000);

        data.putaran = pulseCount;
        strcpy(data.rumah, "A_3");
        data.timestamp = millis() + timeOffset;

        sendData(data);
        //Serial.println(success ? "Packet berhasil dikirim" : "Gagal kirim paket");
        on = 1;
      }
    }
    LoRa.sleep();
  }
}

void hall1Interrupt() {
  isHall1Triggered = true;
}

void sendData(PutaranData &data) {
  byte dataBuffer[sizeof(PutaranData)];
  memcpy(dataBuffer, &data, sizeof(PutaranData));

  LoRa.beginPacket();
  LoRa.write(dataBuffer, sizeof(PutaranData));
  LoRa.endPacket();

  Serial.print("Data dikirim: Rumah: ");
  Serial.print(data.rumah);
  Serial.print(", Putaran: ");
  Serial.print(data.putaran);
  Serial.print(", Timestamp: ");
  Serial.println(data.timestamp);

  LoRa.sleep();
}

void checkForSync() {
  int packetSize = LoRa.parsePacket();
  if (packetSize) {
    String incoming = "";
    while (LoRa.available()) {
      incoming += (char)LoRa.read();
    }

    if (incoming.startsWith("SYNC,")) {
      String epochStr = incoming.substring(5);
      uint64_t gwEpoch = parseUint64(epochStr);
      gw_epoch_rx = gwEpoch;
      node_ms_rx = millis();
      timeOffset = gw_epoch_rx - node_ms_rx;

      Serial.print("SYNC diterima. Waktu gateway: ");
      Serial.print(gw_epoch_rx);
      Serial.print(", millis(): ");
      Serial.print(node_ms_rx);
      Serial.print(", offset (theta): ");
      Serial.println(timeOffset);
    }
  }
}