# cctv-spring

Versi Spring Boot dari `cctv-laravel`, fitur-fiturnya disamakan dengan versi
Laravel yang sudah jalan. Dibuat untuk dites dulu di PC sebelum pindah ke
server - lokasi folder storage tinggal diganti lewat file `.env`, tidak perlu
ubah kode atau `docker-compose.yml`.

## Peta fitur: Laravel -> Spring Boot

| Laravel (`routes/api.php`)            | Spring Boot                                            |
|----------------------------------------|----------------------------------------------------------|
| `GET /api/ping`                        | `PingController`                                          |
| `POST /api/upload`                     | `CctvController.upload()`                                 |
| `POST /api/register-face`              | `CctvController.registerFace()`                            |
| `GET /api/faces`                       | `CctvController.listFaces()`                               |
| `DELETE /api/faces/{id}`               | `CctvController.deleteFace()`                              |
| `GET /api/logs`                        | `CctvController.logs()`                                    |
| `GET /api/snapshot/{filename}`         | `CctvController.snapshot()`                                |
| `Schedule: recordings:compile` (5 mnt) | `RecordingScheduler.compileRecordings()` (`@Scheduled`)    |
| `Schedule: recordings:cleanup` (harian)| `RecordingScheduler.cleanupOldRecordings()` (`@Scheduled`) |
| `GET /` (Blade)                        | `RecordingViewController.index()` (Thymeleaf)               |
| `GET /recordings/{date}` (Blade)       | `RecordingViewController.show()` (Thymeleaf)                 |

Tabel database: `known_faces`, `face_embeddings`, `detection_logs`,
`recordings` (dibuat otomatis oleh Hibernate saat aplikasi start pertama
kali, lihat `spring.jpa.hibernate.ddl-auto=update`).

> **Perubahan skema:** `known_faces` sekarang cuma menyimpan identitas orang
> (nama). Embedding wajahnya dipindah ke tabel terpisah `face_embeddings`
> (relasi 1-ke-banyak) - 1 orang bisa punya **beberapa foto/embedding**
> sekaligus, bukan cuma 1. Lihat bagian "Registrasi wajah dengan banyak foto"
> di bawah.

## ✅ Kontrak face-service sudah dikonfirmasi

`FaceServiceClient.java` sudah dicocokkan dengan isi `main.py` yang asli:

```
POST http://face-service:8500/detect   (multipart/form-data, field "file")
->
{
  "faces_detected": 2,
  "faces": [
    { "bbox": [x1, y1, x2, y2], "embedding": [...512 float...], "det_score": 0.93 }
  ]
}
```

Catatan: `embedding` dari InsightFace **sudah dinormalisasi** (unit length),
jadi secara matematis cosine similarity di `FaceMatchingService` setara
dengan dot product biasa - tapi kode tetap menghitung pembagian dengan norm
supaya tetap benar meskipun suatu saat model/embedding-nya diganti dan
tidak lagi ternormalisasi.

> **Rekomendasi:** ganti model di `face-service/main.py` dari `buffalo_sc`
> ke `buffalo_l` (`FaceAnalysis(name="buffalo_l", ...)`). `buffalo_sc` adalah
> varian paling ringan/kecil di InsightFace - cepat tapi kurang akurat untuk
> kondisi sulit (sudut CCTV dari atas, pencahayaan backlight, wajah kecil di
> frame). `buffalo_l` jauh lebih akurat untuk kondisi seperti itu, dengan
> trade-off sedikit lebih lambat (masih wajar di CPU modern untuk kebutuhan
> rumahan).

## Registrasi wajah dengan banyak foto

`register-face` sekarang menerima **beberapa file sekaligus** (field
`files`, bentuk jamak), bukan cuma 1. Kalau `name` yang dikirim sudah pernah
didaftarkan sebelumnya, foto-foto baru **ditambahkan** ke orang yang sama
(bukan bikin entry duplikat).

```bash
curl -F "name=Gugun" \
     -F "files=@foto_depan.jpg" \
     -F "files=@foto_samping.jpg" \
     -F "files=@foto_dari_atas.jpg" \
     http://localhost:8090/api/register-face
```

Response:
```json
{
  "id": 1,
  "name": "Gugun",
  "photos_added": 3,
  "photos_failed": 0,
  "details": [
    { "file": "foto_depan.jpg", "status": "sukses" },
    { "file": "foto_samping.jpg", "status": "sukses" },
    { "file": "foto_dari_atas.jpg", "status": "sukses" }
  ]
}
```

Saat matching (`/api/upload`), similarity dihitung terhadap **semua**
embedding dari **semua** orang - similarity tertinggi (di atas
`cctv.face-match-threshold`) yang menentukan hasilnya. Jadi wajah yang mirip
salah satu dari beberapa foto orang tersebut sudah cukup untuk match.

**Tips supaya akurasi maksimal:** foto registrasi sebaiknya **mendekati
kondisi CCTV asli**, bukan cuma selfie frontal sempurna. Sertakan variasi:
- Sudut agak dari atas (mirip posisi kamera CCTV)
- Pencahayaan tidak terlalu ideal (bukan studio lighting)
- Jarak lebih jauh (bukan close-up)

Semakin mirip kondisi foto registrasi dengan kondisi nyata di lapangan,
semakin akurat matching-nya.

## Struktur project

```
cctv-spring/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── .env                          # <- path storage diatur di sini
├── face-service/                 # <- COPY folder ini dari project cctv-laravel (tidak perlu diubah)
└── src/main/
    ├── java/com/homeserver/cctv/
    │   ├── CctvApplication.java
    │   ├── controller/
    │   │   ├── PingController.java
    │   │   ├── CctvController.java          # upload, register-face, faces, logs, snapshot
    │   │   └── RecordingViewController.java # halaman web index + show
    │   ├── entity/
    │   │   ├── KnownFace.java     # tabel known_faces (identitas orang)
    │   │   ├── FaceEmbedding.java # tabel face_embeddings (1 orang -> banyak foto)
    │   │   ├── DetectionLog.java  # tabel detection_logs
    │   │   └── Recording.java     # tabel recordings
    │   ├── repository/            # akses DB (Spring Data JPA)
    │   ├── service/
    │   │   ├── FaceServiceClient.java     # panggil Python face-service
    │   │   ├── FaceMatchingService.java   # cosine similarity ke semua embedding
    │   │   ├── ImageAnnotationService.java# gambar kotak + label (Java2D, pengganti GD)
    │   │   ├── FrameStorageService.java   # simpan frame ke disk
    │   │   └── RecordingService.java      # compile ffmpeg + cleanup
    │   ├── scheduler/RecordingScheduler.java  # @Scheduled, pengganti console.php
    │   ├── dto/                   # bentuk data pertukaran (bukan entity DB)
    │   └── config/                # RestTemplate, static resource handler video
    └── resources/
        ├── application.properties
        └── templates/index.html, show.html   # halaman web (Thymeleaf)
```

## Cara jalanin di PC

### 1. Siapkan folder face-service

Copy apa adanya dari project `cctv-laravel`, tidak perlu diubah (kecuali
kamu mengikuti rekomendasi ganti model ke `buffalo_l` di atas):

```bash
cp -r /path/ke/cctv-laravel/face-service ./face-service
```

### 2. Jalankan semuanya

```bash
cd cctv-spring
docker compose up -d --build
```

Build pertama agak lama (Maven download dependency + build image face-service).
Cek log:
```bash
docker compose logs -f app
```

### 3. Tes endpoint

```bash
curl http://localhost:8090/api/ping
# -> {"status":"ok"}

curl -F "name=Gugun" \
     -F "files=@foto_wajah_gugun.jpg" \
     http://localhost:8090/api/register-face

curl http://localhost:8090/api/faces

curl -X POST http://localhost:8090/api/upload \
  -F "camera_id=cam1" \
  -F "file=@foto_test.jpg"

curl "http://localhost:8090/api/logs?limit=20"
```

Halaman web:
```
http://localhost:8090/
http://localhost:8090/recordings/2026-07-29
```

Paksa compile tanpa nunggu jam berakhir (untuk testing manual) - belum ada
endpoint HTTP untuk ini (di Laravel itu artisan command `--force`), tapi
kamu bisa restart aplikasi setelah ganti sementara cron `@Scheduled` di
`RecordingScheduler` supaya jalan tiap menit, atau saya buatkan endpoint
admin kalau kamu mau (kasih tau saja).

### 4. Lihat frame/video yang tersimpan di PC

```bash
ls storage/frames/cam1/2026-07-29/
ls storage/recordings/cam1/2026-07-29/
```

## Pindah ke server nanti

Cukup ubah 1 baris di `.env`:

```
STORAGE_HOST_PATH=/mnt/storage/cctv-spring
```

lalu `docker compose up -d --build` lagi di server. Tidak ada perubahan kode
atau `docker-compose.yml` yang diperlukan.

## Perbedaan desain dari versi Laravel (dan alasannya)

- **Scheduler jadi 1 proses dengan app** (`@Scheduled`), bukan container
  terpisah seperti `scheduler` di Laravel - Spring bisa jalankan scheduled
  task ringan di proses yang sama tanpa container tambahan.
- **Snapshot URL berbentuk path hierarkis** (`/api/snapshot/cam1/2026-07-29/11/frame_000042.jpg`)
  bukan flat filename seperti contoh di README Laravel yang lama - supaya 1
  file frame bisa dipakai untuk 2 keperluan sekaligus (lihat cepat via API +
  bahan compile video), tidak perlu simpan file 2x.
- **Video diserving lewat static resource handler** (`/media/recordings/**`)
  bukan lewat symlink `public/storage` seperti Laravel, karena Spring Boot
  tidak punya konsep symlink storage seperti itu - jadi dipetakan langsung
  di `WebConfig.java`.
- **1 orang bisa punya banyak foto/embedding** (`face_embeddings`, relasi
  1-ke-banyak ke `known_faces`), berbeda dari versi awal yang cuma simpan 1
  embedding per orang - perubahan ini dibuat untuk mengatasi akurasi face
  recognition yang rendah di kondisi CCTV nyata (sudut, jarak, pencahayaan
  jauh berbeda dari foto selfie registrasi).

## Yang belum dibuat (menyusul, kasih tau kalau mau lanjut ke sini)

- Endpoint admin untuk trigger compile manual (setara `--force`)
- Notifikasi saat wajah "unknown" terdeteksi
- Halaman untuk lihat/hapus wajah terdaftar dari web (sekarang baru API)
- Endpoint untuk hapus 1 foto/embedding spesifik tanpa hapus seluruh orang
  (sekarang `DELETE /api/faces/{id}` hapus orang + semua fotonya sekaligus)