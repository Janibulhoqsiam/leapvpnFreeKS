# LeapVPN

[LeapVPN](https://leapvpn.org/) is a free and open-source VPN application for Android, designed for privacy and security. It is based on the [Leaf](https://github.com/eycorsican/leaf) project and integrates with [Noisy Shuttle](https://github.com/Gowee/noisy-shuttle) for enhanced anonymity and network traffic protection. Additionally, LeapVPN leverages the power of [v2rayNG](https://github.com/2dust/v2rayNG) and [v2ray-core](https://github.com/v2fly/v2ray-core)/[xray-core](https://github.com/XTLS/Xray-core) for robust VPN protocols and security features. LeapVPN is lightweight and simple to use, providing secure internet access for users worldwide.

## Features

- **Secure VPN**: Protects your internet traffic with encryption, ensuring safe browsing and data protection.
- **Privacy Focused**: Uses [Leaf](https://github.com/eycorsican/leaf) and [Noisy Shuttle](https://github.com/Gowee/noisy-shuttle) for enhanced privacy and traffic obfuscation, minimizing the risk of detection.
- **Open Source**: Fully open-source and community-driven. Contributions are welcome to improve the project.
- **Android Support**: Optimized for Android devices with easy-to-use interface.
- **Enhanced Protocol Support**: Integrates [v2rayNG](https://github.com/2dust/v2rayNG) and [v2ray-core](https://github.com/v2fly/v2ray-core)/[xray-core](https://github.com/XTLS/Xray-core) for multiple VPN protocols, improving speed, reliability, and security.

## Dependencies

LeapVPN relies on the following projects:

- [Leaf](https://github.com/eycorsican/leaf): A lightweight VPN client supporting various protocols such as V2Ray, Shadowsocks, and WireGuard.
- [Noisy Shuttle](https://github.com/Gowee/noisy-shuttle): A tool for creating noise traffic to obfuscate VPN traffic, making it harder to detect by deep packet inspection (DPI).
- [v2rayNG](https://github.com/2dust/v2rayNG): A popular Android client for [V2Ray](https://github.com/v2fly/v2ray-core), which provides advanced traffic routing and obfuscation features.
- [v2ray-core](https://github.com/v2fly/v2ray-core)/[xray-core](https://github.com/XTLS/Xray-core): The core library for V2Ray, offering secure, flexible, and scalable VPN protocols.

## Installation

To get started with LeapVPN, follow these steps:

1. **Clone the Repository**

   Clone the repository to your local machine:

   ```bash
   git clone https://github.com/LeapVPN/leapvpn.git
   cd leapvpn
   ```

2. **Build the Application**

   Build AndroidLibV2rayLite and copy the aar and so files to the libs folder.

   Use Android Studio or the following commands to build the application:

   ```bash
   sh buildjni.sh
   ./gradlew assembleRelease
   ```

3. **Install the APK**

   After building the APK, sign and install it on your Android device. You can use Android's `adb` tool or manually install the APK through the device's file manager.

## Usage

1. Launch the LeapVPN app on your Android device.
2. Configure the VPN by entering your server details or importing an existing configuration file.
3. Toggle the VPN connection on or off within the app.

LeapVPN supports multiple VPN protocols and offers the flexibility to switch between them. For advanced users, the app also supports configurations for custom VPN server settings.

## Server Configuration Best Practices

Setting up your own VPN server is crucial for optimal performance and security. Here are some best practices for configuring various backend servers:

### Xray Server Configuration

For Xray server setup, refer to the official documentation and examples:
- [Xray-core Usage](https://github.com/XTLS/Xray-core?tab=readme-ov-file#usage)
- VLESS-XTLS-uTLS-REALITY configuration is recommended for highest performance and security
- XTLS Vision protocol provides excellent performance with enhanced obfuscation

Example configuration paths can be found in the Xray GitHub repository under usage examples.

### V2Ray Server Configuration

For V2Ray server configuration, follow the guidelines at:
- [V2Fly Configuration Overview](https://www.v2fly.org/config/overview.html)
- Configure appropriate security levels based on your needs
- Set proper log levels for troubleshooting (recommended: "warning" for production, "info" for debugging)

### Noisy Shuttle Configuration

To enhance traffic obfuscation and bypass deep packet inspection, configure Noisy Shuttle:

```bash
noisy-shuttle server 0.0.0.0:443 www.example.com:443 ${PASSWORD} -qq
```

Replace `${PASSWORD}` with your secure password and `www.example.com:443` with your destination server.

Properly configured servers will significantly improve connection stability, reduce latency, and enhance security measures. We recommend regularly updating your server configurations to address emerging security vulnerabilities and take advantage of protocol improvements.

## Contributing

We welcome contributions to LeapVPN! If you want to help improve the app, please fork the repository, create a branch, and submit a pull request with your changes. Whether it's a bug fix, feature addition, or documentation improvement, your contributions are greatly appreciated.

Before contributing, please ensure that your code follows the project's style guide and includes appropriate tests if applicable.

## License

LeapVPN is licensed under the [GPL-3.0 License](https://opensource.org/licenses/GPL-3.0).

## Acknowledgments

- [Leaf](https://github.com/eycorsican/leaf) for providing the lightweight VPN framework.
- [Noisy Shuttle](https://github.com/Gowee/noisy-shuttle) for assisting with obfuscating VPN traffic.
- [v2rayNG](https://github.com/2dust/v2rayNG), [xray-core](https://github.com/XTLS/Xray-core) and [v2ray-core](https://github.com/v2fly/v2ray-core) for enabling robust protocol support and advanced network traffic features.
- The open-source community for their continuous contributions and improvements.

## Contact

For questions or feedback, feel free to open an issue on the [GitHub repository](https://github.com/LeapVPN/leapvpn/issues) or contact us directly via email or social media.
