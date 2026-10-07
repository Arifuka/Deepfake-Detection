# Localized Deepfake & AI-Generated Media Detection Pipeline

A privacy-centric, edge-optimized deepfake detection framework designed for local mobile device deployment. This system utilizes a lightweight MobileNetV3 architecture combined with multi-domain feature extraction to identify digital manipulations in raw video streams.

#Framework Architecture & Pipeline Workflow

The project is structured into distinct, modular layers to optimize processing speed on edge devices:
1. External Source and Data Collection: Sourcing from official benchmarks and handling live on-device frame captures.
2. Initial Heuristic Check: A rapid watermark and known AI-label scanner layer to bypass heavy ML computations when obvious metadata exists.
3. Preprocessing & Feature Extraction: Utilizes MediaPipe/BlazeFace for background noise reduction and face cropping (resized strictly to `224x224x3`). Extracts spatial artifacts (LBP, GLCM) and frequency-domain flaws (FFT).
4. Model Training & Optimization: Transfer learning loop via MobileNetV3 minimized with Binary Cross-Entropy loss and optimized into a quantized 8-bit `model.tflite` asset.
5. Decision & Actuation Layer: Employs a rolling 5-frame safety buffer to smooth visual glitches and trigger real-time UI system warnings.
6. Reporting & Deployment: Volatile memory purging from RAM to ensure zero user privacy leaks and metadata logging.



## Dataset Notice & Terms of Use

This project utilizes the official Celeb-DF (v2) dataset for training and validation evaluation. 

In strict compliance with the dataset's Data Use Agreement (DUA):
* No Data Distribution: Raw video files, extracted frame tensors, and final trained weights are completely excluded from this repository via local `.gitignore` rules to prevent unauthorized redistribution.
* Academic Purposes Only: This system is developed solely for non-commercial, academic research as part of a final year project at IIUM.
* Access Request: External researchers wishing to replicate this training pipeline must request dataset access directly from the original creators at the official [Celeb-DF Repository](https://github.com).


## Development Environment Setup

### Prerequisites
* Python 3.10+
* Google Colab (with connected T4 GPU runtime environment)

### Core Dependencies
Ensure the following libraries are initialized in your workspace:
```bash
pip install tensorflow mediapipe opencv-python scikit-learn numpy
```

---

## 📁 Project Directory Schema
```text
├── dataset/               # Isolated via .gitignore (Local/Cloud storage only)
│   ├── train/             # 80% Stratified Training Split (Real / Fake folders)
│   └── validation/        # 20% Stratified Validation Split (Real / Fake folders)
├── scripts/
│   ├── preprocess.py      # MediaPipe Face Cropping & Frame Sampling
│   └── train_pipeline.py  # MobileNetV3 Transfer Learning & TFLite Quantization
├── .gitignore             # Strict exclusion matrix for model weights & raw videos
├── LICENSE                # MIT Open-Source Code License
└── README.md              # Project Documentation Matrix
```

---

## Code License
The code implementation within this repository is open-sourced under the MIT License.
