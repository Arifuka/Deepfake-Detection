# EdgeGuard: On-Device Deepfake Detection for Elderly Users

Final Year Project, International Islamic University Malaysia (IIUM), 2026/2027.

## Overview
A mobile app that detects likely deepfake videos on-device and warns elderly users in clear language. Targeting TikTok users in Malaysia's Klang Valley.

## Status
Work in progress

## Objectives
The primary goal of this project is to develop a secure, real-time mobile application that detects AI-generated content on social media, with design focus on usability and trustworthiness for elderly users in Malaysia. Specific objectives include:

-To develop a real-time mobile application that helps elderly users detect AI-generated videos on TikTok
-To evaluate the application by testing its usability and effectiveness with elderly users in Klang Valley
-To build user trust by ensuring secure data protection and secure use of the application by implementing the Secure Software Development Life Cycle (SSDLC)


## Architecture
![Framework](docs/EdgeGuardFramework_v2.png)
Two parts: an offline training pipeline (Celeb-DF v2, MobileNetV3, INT8 TFLite) and a real-time on-device pipeline (Android).

## Dataset
Trained and evaluated on Celeb-DF v2 only. The dataset is NOT included in this repository. Request access from the official source: [[paste link](https://github.com/yuezunli/celeb-deepfakeforensics)].

## Results
Work in progress

## Limitations
- Single dataset, so results do not prove generalization to TikTok content.
- Android only.

## How to run
[Fill in later: Colab notebook name, how to open the Android project.]

## License and ethics
This project is licensed under the **MIT License**. 

We chose this license because **EdgeGuard** is designed to be an open framework for on-device security. We want to encourage academic research, community experimentation, and widespread adoption. The MIT License allows anyone to freely clone, modify, and integrate this pipeline into their own projects—both open-source and commercial—without restrictive bottlenecks. No real user data or face images are stored in this repository.
