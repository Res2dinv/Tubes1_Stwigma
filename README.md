# Tugas Besar 1 IF2211 Strategi Algoritma 2026
> Battlecode 2025 — Java Scaffold

---

## 👥 Authors

| Nama | NIM |
|------|-----|
| Daniel Anindito Nugroho | 13524002 |
| Syaqina Octavia Rizha | 13524088 |
| Ishaq Irfan Farizal | 13524094 |

---

## 🤖 Deskripsi Bot

### 1. `control`
Bot berbasis **greedy intensif** yang mengoptimalkan setiap aspek operasional secara mandiri. Navigasi menggunakan evaluasi 5 arah terdekat dengan sistem penalti tile, dilengkapi *bug navigation* untuk menghindari rintangan. Setiap unit bertindak egois — Soldier mengejar tower musuh, Mopper menyapu batas wilayah, Splasher memilih titik ledakan dengan dampak terluas. Manajemen paint dikendalikan ketat dengan mode *emergency refill* saat cadangan kritis, sementara tipe tower dipilih otomatis via algoritma modulo tanpa koordinasi antar unit.

### 2. `endthisgng`
Bot berbasis **dua unit (Soldier & Splasher)** yang dikoordinasi Paint Tower dengan rasio komposisi greedy. Splasher agresif mengekspansi ke segala arah menggunakan sistem skor tile, sementara Soldier beroperasi dalam tiga prioritas terurut: cari ruin → cat pola tower → ekspansi area. Tower hanya fokus pada dua hal: serang musuh ber-HP rendah, atau spawn unit sesuai rasio lokal (Splasher diprioritaskan jika rasionya < 60%). Unit kembali ke tower saat paint < 60.

### 3. `abyss`
Bot paling **koordinatif** dari ketiganya, mengandalkan Soldier dan Mopper dengan komunikasi aktif antar unit. Early game sebagian robot dipakai untuk verifikasi simetri peta, hasilnya disebarkan tower ke seluruh unit. Spawn unit bersifat reaktif — Mopper diprioritaskan saat ada *request* pembersihan ruin, Soldier menjadi tulang punggung ekspansi. Seluruh keputusan dibuat dari sensing lokal di awal giliran, menjaga bytecode tetap efisien sekaligus mempertahankan koordinasi tim yang lebih terstruktur dibanding dua bot lainnya.

### Perbandingan

| Aspek | `control` | `endthisgng` | `abyss` |
|-------|-----------|--------------|---------|
| Unit utama | Soldier, Mopper, Splasher | Soldier, Splasher | Soldier, Mopper |
| Koordinasi | Mandiri | Rasio lokal | Komunikasi aktif |
| Navigasi | Greedy + bug nav | Greedy + random | Greedy lokal |
| Paint management | Emergency mode | Threshold 60 | Sensing lokal |

---

## 📁 Project Structure

```
.
├── README.md               # File ini
├── build.gradle            # Gradle build file
├── gradle.properties       # Konfigurasi project
├── gradlew                 # Gradle wrapper (Unix/macOS)
├── gradlew.bat             # Gradle wrapper (Windows)
├── gradle/                 # File pendukung Gradle wrapper
├── src/                    # Source code bot
├── test/                   # Test code
├── client/                 # Client untuk menjalankan match
├── build/                  # Output kompilasi (dapat diabaikan)
├── matches/                # Output file match
└── maps/                   # Custom maps
```

---

## 🚀 How to Get Started

Kamu bebas mengedit langsung `examplefuncsplayer`, namun disarankan membuat bot baru dengan menyalin folder tersebut ke package baru di dalam `src/`.

---

## 🛠️ Useful Commands

| Command | Fungsi |
|---------|--------|
| `./gradlew build` | Kompilasi player |
| `./gradlew run` | Jalankan game sesuai `gradle.properties` |
| `./gradlew update` | Update ke versi terbaru (jalankan sering!) |
| `./gradlew zipForSubmit` | Buat file zip untuk submit |
| `./gradlew tasks` | Lihat semua task yang tersedia |

> **Windows:** Gunakan `gradlew` (tanpa `./`) di Command Prompt, atau `./gradlew` di PowerShell.

---

## ⚙️ Configuration

Konfigurasi project tersedia di `gradle.properties`.

Jika mengalami masalah dengan client default, laporkan ke devs dan coba set:
```properties
compatibilityClient=true
```
untuk mengunduh versi client alternatif.
