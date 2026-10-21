# EdgeGuard: On-Device Deepfake Detection for Elderly Users

Final Year Project, International Islamic University Malaysia (IIUM), 2026/2027.

## Overview
A mobile app that detects likely deepfake videos on-device and warns elderly users in clear language. Targeting TikTok users in Malaysia's Klang Valley.

## Status
Work in progress.
- [x] Identity-disjoint data split (Celeb-DF v2)
- [x] Face-crop extraction
- [x] MobileNetV3 trained (validation results below)
- [ ] Test-set evaluation (AUC, F1, confusion matrix, per-video results)
- [ ] Threshold tuning and INT8 TFLite export
- [ ] Android app (video scoring, smoothing, warning screen)
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

## Results so far (validation only)
| Phase | Train AUC | Val AUC |
|---|---|---|
| 1 (frozen base) | 0.751 | 0.687 |
| 2 (fine-tuned) | 0.984 | 0.963 |

These are per-image scores on only 6 validation identities, and the same data was used to select the best epoch, so they are optimistic. Test-set results will be added after evaluation.

## Limitations
- Single dataset: results do not show generalization to TikTok or other deepfake methods.
- Small validation (6 identities) and test (12 identities) sets give noisy metrics.
- Many fake videos were dropped to keep the split identity-disjoint.
- Scores so far are per image, not per video.
- Compression augmentation is a simulation of real platform compression.
- The Android app is not built yet, and iOS is not planned.

## Repository structure
- `model/` notebook and `splits.csv`
- `android-app/` Android Studio project
- `docs/` framework diagram
- `results/` evaluation outputs (to be added)

## How to reproduce
1. Request and download Celeb-DF v2, and place the zip in your Google Drive.
2. Open `model/deepfake_detection.ipynb` in Google Colab with a T4 GPU.
3. Run the cells in order: unzip, split, face extraction, training. Adjust the Drive paths to match yours.

## License and ethics
This project is licensed under the **MIT License**. 

We chose this license because **EdgeGuard** is designed to be an open framework for on-device security. We want to encourage academic research, community experimentation, and widespread adoption. The MIT License allows anyone to freely clone, modify, and integrate this pipeline into their own projects—both open-source and commercial—without restrictive bottlenecks. No real user data or face images are stored in this repository.
