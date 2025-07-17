#include <SPI.h>
#include <LoRa.h>

// Pin untuk LoRa
#define SS 5     // Pin CS
#define RST 14   // Pin Reset
#define DIO0 2   // Pin DIO0

// Struktur untuk data yang diterima
struct PutaranData {
  char rumah[10];   // Nama rumah, misalnya "A_1"
  int putaran;      // Jumlah putaran
};

void setup() {
  Serial.begin(115200);
  
  // Setup LoRa
  LoRa.setPins(SS, RST, DIO0);  // Tentukan pin CS, Reset, DIO0
  if (!LoRa.begin(433E6)) {  // Frekuensi LoRa 433 MHz
    Serial.println("LoRa tidak terhubung");
    while (1);  // Jika LoRa gagal terhubung, berhenti
  }
  Serial.println("LoRa Initialized");
}

void loop() {
  // Mengecek apakah ada data yang diterima
  int packetSize = LoRa.parsePacket();
  if (packetSize) {
    // Membaca data yang diterima
    byte receivedBuffer[packetSize];
    LoRa.readBytes(receivedBuffer, packetSize);

    // Deserialisasi byte array kembali ke struct
    PutaranData receivedData;
    memcpy(&receivedData, receivedBuffer, sizeof(PutaranData));
    int rssi = LoRa.packetRssi();
    // Menampilkan data yang diterima
    Serial.print("Data diterima: Rumah: ");
    Serial.print(receivedData.rumah);
    Serial.print(", Putaran: ");
    Serial.print(receivedData.putaran);
    Serial.print(", RSSI: ");
    Serial.print(rssi);
  }
}