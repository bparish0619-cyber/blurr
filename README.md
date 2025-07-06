# 🐼 Panda: Your Personal AI Phone Operator

**You touch grass. I'll touch your glass.**

---

![Panda AI Home Screen](https://github.com/user-attachments/assets/9ef3993c-9719-448e-a968-d6889373a677) 


**Panda** is a proactive, on-device AI agent for Android that autonomously understands natural language commands and operates your phone's UI to achieve them. Inspired by the need to make modern technology more accessible, Panda acts as your personal operator, capable of handling complex, multi-step tasks across different applications.

[![Project Status: WIP](https://img.shields.io/badge/project%20status-wip-yellow.svg)](https://wip.vost.pt/)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Kotlin Version](https://img.shields.io/badge/Kotlin-1.9.22-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)

## Core Capabilities

* 🧠 **Intelligent UI Automation:** Panda sees the screen, understands the context of UI elements, and performs actions like tapping, swiping, and typing to navigate apps and complete tasks.
* 🌐 **Deep Web Research:** When a task requires real-world information, Panda automatically uses the **Tavily Search API** to perform intelligent web searches, analyze the results, and use that knowledge to inform its actions.
* 💾 **Persistent & Personalized Memory:** Powered by **Mem0**, Panda remembers key facts about you and learned procedures across sessions. It learns your preferences, contacts, and habits to become a truly personalized assistant over time.
* 🛡️ **Proactive Content Moderation:** Define topics or types of content you wish to avoid, and Panda will actively monitor the screen and intervene to prevent that content from being shown.

## Architecture Overview

Panda is built on a sophisticated multi-agent system written entirely in Kotlin. This architecture separates responsibilities, allowing for more complex and reliable reasoning.

* **Eyes & Hands (The Actuator):** The **Android Accessibility Service** serves as the agent's physical connection to the device, providing the low-level ability to read the screen element hierarchy and programmatically perform touch gestures.
* **The Brain (The LLM):** All high-level reasoning, planning, and analysis are powered by **Google's Gemini** models. This is where decisions are made.
* **The Library (Knowledge & Memory):**
    * **Tavily Search** provides real-time web access.
    * **Mem0** provides a persistent, long-term memory layer, allowing the agent to learn.
* **The Agent Team:**
    * **Manager:** The strategist. Analyzes the user's goal and memory to create a high-level plan.
    * **Operator:** The executor. Takes a single step from the Manager's plan and determines the precise UI action.
    * **Reflector:** The analyst. Observes the result of an action to determine success or failure, providing feedback for the next cycle.
    * **DeepSearch Agent:** The researcher. Decides when a task requires external information and orchestrates the web search loop.
    * **Judge:** The moderator. A specialized agent that runs the content filtering logic.

## 🚀 Getting Started

### Prerequisites
* Android Studio (latest version recommended)
* An Android device or emulator with API level 26+
* API keys for Gemini, Tavily, and Mem0.

### Installation

1.  **Clone the repository:**
    ```bash
    git clone [https://github.com/ayush0chaudhary/blurr.git](https://github.com/ayush0chaudhary/blurr.git)
    cd blurr
    ```

2.  **Set up API Keys:**
    This project uses a `local.properties` file to securely handle API keys. This file is included in `.gitignore` and should never be committed.
    * Create a file named `local.properties` in the root directory of the project.
    * Add your API keys to this file in the following format:
        ```properties
        GEMINI_API_KEYS="your_gemini_key_1,your_gemini_key_2"
        TAVILY_API_KEY="your_tavily_api_key"
        MEM0_API_KEY="your_mem0_api_key"
        ```

3.  **Build & Run:**
    * Open the project in Android Studio.
    * Let Gradle sync all the dependencies.
    * Run the app on your selected device or emulator.

4.  **Enable Accessibility Service:**
    * On the first run, the app will prompt you to grant Accessibility permission.
    * Click "Grant Access" and enable the "Panda" service in your phone's settings. This is required for the agent to see and control the screen.

## 🗺️ What's Next for Panda (Roadmap)

Panda is currently a powerful proof-of-concept, and the roadmap is focused on making it a truly indispensable assistant.

* [ ] **Interactive Dialogue:** Implement the ability for the agent to ask clarifying questions instead of making assumptions.
* [ ] **Advanced Voice I/O:** Integrate a high-quality voice synthesis model like ElevenLabs for more natural and expressive interaction.
* [ ] **Hybrid Perception Model:** Give the agent a "toolbox" of native Android APIs (e.g., using Intents to open apps directly) for faster, more reliable execution on common tasks.
* [ ] **Advanced Multimodal Understanding:** Explore models that can understand not just screenshots, but also short video clips of screen interactions to better comprehend animations and transitions.

## 🤝 Contributing

Contributions are welcome! If you have ideas for new features or improvements, feel free to open an issue or submit a pull request.

## 📜 License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details.

### A small video to help you understand what the project is about. 
https://github.com/user-attachments/assets/b577072e-2f7f-42d2-9054-3a11160cf87d

Write you api key in in local.properties, more keys you use, better is the speed 😉


# Pull latest log file
adb pull /data/data/com.example.blurr/files/gemini_logs/

# View logs in real-time
adb logcat | grep GeminiApi