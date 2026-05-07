# Talko — WhatsApp-style Chat & Calling App

A production-ready Android chat application built with Kotlin, Jetpack Compose, Firebase, and Agora RTC.

## Features
- 📱 Email/password authentication (Firebase Auth)
- 💬 Real-time messaging (Firestore + Room offline-first)
- 🖼️ Image & voice message sending (Firebase Storage)
- 📞 Audio & video calling (Agora RTC)
- 👥 Group chats
- 🔔 Push notifications (FCM)
- 🌙 Dark mode
- 🔍 Chat search
- 📵 Offline support with message queuing

## Setup

### 1. Firebase
1. Create a project at [Firebase Console](https://console.firebase.google.com)
2. Add an Android app with package `com.talko.app`
3. Download `google-services.json` → place in `app/`
4. Enable **Email/Password** authentication
5. Enable **Firestore**, **Storage**, and **Cloud Messaging**
6. Set Firestore rules (see below)

### 2. Agora (Calling)
1. Create a free project at [Agora Console](https://console.agora.io)
2. Copy `app/src/main/java/com/talko/app/core/call/AgoraConfig.kt.example`
   → rename to `AgoraConfig.kt`
3. Replace `YOUR_AGORA_APP_ID` with your App ID

### 3. Firestore Security Rules
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    match /chats/{chatId} {
      allow read, write: if request.auth != null && request.auth.uid in resource.data.participants;
      allow create: if request.auth != null;
      match /messages/{messageId} {
        allow read, write: if request.auth != null;
      }
    }
    match /calls/{callId} {
      allow read, write: if request.auth != null;
    }
  }
}
```

## Tech Stack
- **UI**: Jetpack Compose + Material3
- **DI**: Hilt
- **Database**: Room (offline-first)
- **Backend**: Firebase (Auth, Firestore, Storage, FCM)
- **Calling**: Agora RTC SDK 4.3.1
- **Image loading**: Coil
- **Architecture**: MVVM + Clean Architecture

## Project Structure
```
app/src/main/java/com/talko/app/
├── core/           # DI, navigation, permissions, session
├── data/           # Repositories, Room DB, remote data sources
├── domain/         # Models, repository interfaces, use cases
├── feature/        # UI screens and ViewModels
│   ├── auth/       # Login, register, profile setup
│   ├── call/       # Audio/video call screens
│   ├── calls/      # Call history tab
│   ├── chat/       # Chat screen
│   ├── group/      # Group creation
│   ├── home/       # Chat list
│   ├── profile/    # Profile screen
│   ├── settings/   # Settings screen
│   └── splash/     # Splash screen
└── ui/theme/       # Colors, typography, theme
```
