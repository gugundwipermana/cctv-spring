"""
Face Service - microservice AI murni (tanpa database, tanpa business logic).
Tugasnya cuma satu: terima gambar -> return daftar wajah + embedding (vektor 512 dimensi).
Laravel yang akan menyimpan hasil ini ke PostgreSQL dan melakukan pencocokan (matching).
"""

import numpy as np
import cv2
from fastapi import FastAPI, UploadFile, File, HTTPException
from insightface.app import FaceAnalysis

app = FastAPI(title="Face Detection & Embedding Service")

# buffalo_sc = model ringan InsightFace, jalan lancar di CPU (tidak butuh GPU).
# buffalo_l = (varian besar) kemungkinan akan langsung membantu banyak untuk kasus sudut/pencahayaan sulit seperti foto CCTV kamu
# Model otomatis di-download saat container pertama kali start (butuh internet sekali saja).
_face_app = FaceAnalysis(name="buffalo_l", providers=["CPUExecutionProvider"])
_face_app.prepare(ctx_id=0, det_size=(320, 320))


def decode_image(image_bytes: bytes):
    np_arr = np.frombuffer(image_bytes, np.uint8)
    return cv2.imdecode(np_arr, cv2.IMREAD_COLOR)


@app.get("/")
def root():
    return {"status": "ok", "service": "face-detection-embedding"}


@app.post("/detect")
async def detect(file: UploadFile = File(...)):
    """
    Input: 1 file gambar (JPEG/PNG).
    Output: daftar wajah yang terdeteksi, masing-masing dengan bbox, embedding, dan skor deteksi.
    """
    image_bytes = await file.read()
    img = decode_image(image_bytes)

    if img is None:
        raise HTTPException(status_code=400, detail="Gambar tidak valid / gagal decode")

    faces = _face_app.get(img)

    results = []
    for f in faces:
        results.append(
            {
                "bbox": f.bbox.astype(int).tolist(),
                "embedding": f.normed_embedding.tolist(),  # sudah dinormalisasi (unit length), 512 float
                "det_score": float(f.det_score),
            }
        )

    return {"faces_detected": len(results), "faces": results}
