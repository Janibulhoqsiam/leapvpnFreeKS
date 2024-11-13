# LeapVPN

LeapVPN is a free and open-source VPN application for Android, designed for privacy and security. It is based on the [Leaf](https://github.com/eycorsican/leaf) project and integrates with [Noisy Shuttle](https://github.com/Gowee/noisy-shuttle) for enhanced anonymity and network traffic protection. LeapVPN is lightweight and simple to use, providing secure internet access for users worldwide.

## Features

- **Secure VPN**: Protects your internet traffic with encryption.
- **Privacy Focused**: Based on Leaf and Noisy Shuttle for enhanced privacy and security.
- **Open Source**: Fully open-source and community-driven. Contributions are welcome!
- **Android Support**: Designed for Android devices.

## Dependencies

LeapVPN relies on the following projects:

- [Leaf](https://github.com/eycorsican/leaf): A lightweight VPN client that supports multiple protocols.
- [Noisy Shuttle](https://github.com/Gowee/noisy-shuttle): A tool for creating noise traffic to obfuscate VPN traffic, making it harder to detect.

## Installation

1. **Clone the Repository**

   First, clone the repository to your local machine:

   ```bash
   git clone https://github.com/LeapVPN/leapvpn.git
   cd leapvpn
   ```

2. **Build the Application**

   To build the application, use Android Studio or the following commands:

   ```bash
   sh buildjni.sh
   ./gradlew assembleRelease
   ```

3. **Install the APK**

   After building the APK, you can sign and install it on your Android device:

## Usage

1. Launch the LeapVPN app on your Android device.
2. Configure the VPN by entering your server details or using an existing configuration file.
3. Toggle the VPN connection on or off within the app.

## Contributing

We welcome contributions to LeapVPN! If you want to help, please fork the repository, create a branch, and submit a pull request with your changes.

## License

LeapVPN is licensed under the [Apache License 2.0](#Apache-2.0-1-ov-file).

## Acknowledgments

- [Leaf](https://github.com/eycorsican/leaf) for providing a lightweight VPN framework.
- [Noisy Shuttle](https://github.com/Gowee/noisy-shuttle) for helping to obfuscate traffic.
- The open-source community for continuous contributions and improvements.

## Contact

For questions or feedback, feel free to open an issue on the [GitHub repository](https://github.com/LeapVPN/leapvpn/issues) or contact us directly.
