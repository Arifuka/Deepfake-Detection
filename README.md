# EdgeGuard: On-Device Deepfake Detection for Elderly Users

Final Year Project, International Islamic University Malaysia (IIUM), 2026/2027.

## Overview
A mobile app that detects likely deepfake videos on-device and warns elderly users in clear language. Targeting TikTok users in Malaysia's Klang Valley.

## Status
Work in progress.
- [x] Identity-disjoint data split (Celeb-DF v2)
- [x] Face-crop extraction
- [x] MobileNetV3 trained
- [x] Test-set evaluation (video-level and frame-level)
- [x] Alert threshold chosen on validation, checked once on test
- [x] FP16 TFLite export (INT8 tried and rejected, see Results)
- [ ] Grad-CAM figures
- [ ] Android app (live capture, scoring, smoothing, overlay alert, history)
- [ ] Live test on TikTok video
- [ ] Usability evaluation with elderly users

## Objectives
The primary goal of this project is to develop a secure, real-time mobile application that detects AI-generated content on social media, with design focus on usability and trustworthiness for elderly users in Malaysia. Specific objectives include:

-To develop a real-time mobile application that helps elderly users detect AI-generated videos on TikTok
-To evaluate the application by testing its usability and effectiveness with elderly users in Klang Valley
-To build user trust by ensuring secure data protection and secure use of the application by implementing the Secure Software Development Life Cycle (SSDLC)


## Architecture
![Framework](docs/EdgeGuardFramework_v2.png)
Two parts: an offline training pipeline (Celeb-DF v2, MobileNetV3, INT8 TFLite) and a real-time on-device pipeline (Android).

## Dataset
Celeb-DF v2 only: 590 real and 5,639 fake videos. The dataset is NOT included in this repository, and no face images are included either. Request access from the official source: https://github.com/yuezunli/celeb-deepfakeforensics and follow its Terms to Use.

Citation:
Li, Y., Yang, X., Sun, P., Qi, H., Lyu, S. "Celeb-DF: A Large-Scale Challenging Dataset for DeepFake Forensics." CVPR 2020.

### Identity-disjoint split
Each fake video involves two identities. The 59 identities were split into 41 train, 6 validation and 12 test (seed 42), so no person appears in more than one split. A fake video is kept only if all its identities fall in the same split. As a result, 2,632 of the 5,639 fakes were dropped, and 3,597 videos are used.

| Split | Real videos | Fake videos |
|---|---|---|
| Train | 409 | 2,810 |
| Val | 60 | 39 |
| Test | 121 | 158 |

The split is in `model/splits.csv`. Because of this stricter split, results are not directly comparable to published Celeb-DF numbers.

## Method so far
- **Face detection:** MediaPipe BlazeFace (short range). The largest face is cropped with 20% padding and resized to 224x224. 14 train videos had no usable face and were skipped.
- **Frames per video:** train uses 20 frames per real video and 3 per fake video to balance classes. Validation and test use 10 per video.
- **Crops:** train 8,047 real / 8,360 fake; val 595 / 387; test 1,205 / 1,580.
- **Model:** MobileNetV3-Large (ImageNet weights), binary classifier, binary cross-entropy loss, Adam.
- **Training:** phase 1 trained the new head only (lr 1e-3, 5 epochs); phase 2 fine-tuned the full network (lr 1e-5, 10 epochs). The best epoch was chosen by validation AUC.
- **Augmentation (train only):** random JPEG compression (quality 30-75), horizontal flip, brightness change. This approximates social-media compression.
- **Export:** converted to TensorFlow Lite with FP16 weights (`model/edgeguard_model.tflite`, about 6 MB). Input: float32 [1, 224, 224, 3], pixel values 0-255. Output: probability that the face is fake.
- **Alert rule:** the app warns when at least 4 of the last 5 frame scores exceed 0.70, and clears the alert below 0.40. The 0.70 level was chosen on validation, then checked once on test. The 0.40 clear level is a design choice and was not tuned.

## Results
Evaluated once on the held-out test split: 12 identities never seen in training or validation (279 videos, 2,785 face crops).

| Metric (test) | Value |
|---|---|
| Video-level AUC (mean of frame scores) | 0.991 |
| Frame-level AUC | 0.967 |
| Frame-level F1 at threshold 0.5 | 0.899 |
| Fake frames caught / real frames wrongly flagged (at 0.5) | 85.9% / 6.8% |
| Alert rule (4 of 5 frames above 0.70): fake videos caught | 82% |
| Alert rule: real videos wrongly alerted | about 1% |

Validation AUC (frame level): <FILL IN: your phase 2 best val AUC> (6 identities, so noisy).

Training was run twice; the first run scored video AUC 0.994 and the final run 0.991, so differences of about 0.003 are run-to-run noise. The reported model and numbers are from the final run.

### Export check (600 test frames)
| Version | AUC | Size |
|---|---|---|
| Keras | 0.968 | 35 MB |
| TFLite INT8 | 0.645 | 3.5 MB |
| TFLite FP16 (used) | 0.970 | 6.0 MB |

INT8 post-training quantization badly reduced accuracy, so FP16 is used.

## Limitations
- Single dataset: Celeb-DF v2 has one fake-generation method. These scores do not show how the model performs on TikTok or other deepfake methods. That is tested separately in the live app.
- Small validation (6 identities) and test (12 identities) sets give noisy metrics; the alert threshold was chosen on a small validation set.
- Many fake videos were dropped to keep the split identity-disjoint.
- Single training run per reported model; no repeated seeds or confidence intervals.
- Compression augmentation only approximates real platform compression.
- Grad-CAM is a visual aid and does not prove the model is right.
- Android only; iOS is not planned.

## Repository structure
- `model/` notebook, `splits.csv`, and `edgeguard_model.tflite`
- `android-app/` Android Studio project
- `docs/` framework diagram
- `results/` evaluation outputs and Grad-CAM figures (to be added)

## How to reproduce
1. Request and download Celeb-DF v2, and place the zip in your Google Drive.
2. Open `model/deepfake_detection.ipynb` in Google Colab with a T4 GPU.
3. Run the cells in order: unzip, split, face extraction, training, test evaluation, threshold selection, TFLite export. Adjust the Drive paths to match yours.

## License and ethics
This project is licensed under the **MIT License**. 

We chose this license because **EdgeGuard** is designed to be an open framework for on-device security. We want to encourage academic research, community experimentation, and widespread adoption. The MIT License allows anyone to freely clone, modify, and integrate this pipeline into their own projects—both open-source and commercial—without restrictive bottlenecks. No real user data or face images are stored in this repository.
