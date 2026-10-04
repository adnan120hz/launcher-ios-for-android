import SwiftUI

/// Settings & Info — same content as the Android app: camera preference,
/// Developer Adnan.120hz credit with working links, license, and the
/// dynamic version with the GitHub update notice when one exists.
struct SettingsView: View {
    @ObservedObject var vm: CameraViewModel
    @Environment(\.openURL) private var openURL
    private let yellow = Color(red: 1.0, green: 0.8, blue: 0.0)

    var body: some View {
        ZStack {
            Color(red: 0.043, green: 0.043, blue: 0.051).ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 6) {
                        Button {
                            vm.settingsOpen = false
                        } label: {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 17, weight: .semibold))
                                .foregroundStyle(.white)
                                .frame(width: 34, height: 34)
                        }
                        .buttonStyle(.plain)
                        Text("Pengaturan & Info")
                            .font(.system(size: 19, weight: .bold))
                            .foregroundStyle(.white)
                    }
                    .padding(.bottom, 18)

                    sectionTitle("KAMERA")
                    card {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Grid").font(.system(size: 15)).foregroundStyle(.white)
                                Text("Garis bantu komposisi 3×3 di viewfinder")
                                    .font(.system(size: 11)).foregroundStyle(Color.white.opacity(0.5))
                            }
                            Spacer()
                            Toggle("", isOn: Binding(
                                get: { vm.gridOn },
                                set: { vm.gridOn = $0; vm.persist() }
                            ))
                            .labelsHidden()
                            .tint(Color(red: 0.2, green: 0.78, blue: 0.35))
                        }
                        .padding(.horizontal, 16).padding(.vertical, 10)
                    }
                    Text("Resolusi & frame rate video diatur dari pill format di mode Video; mode dan kontrol yang tidak didukung perangkat tampil redup dan tidak dapat dipilih.")
                        .font(.system(size: 11)).foregroundStyle(Color.white.opacity(0.45))
                        .padding(.top, 8)

                    sectionTitle("KREDIT").padding(.top, 24)
                    card {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Developer Adnan.120hz")
                                .font(.system(size: 17, weight: .bold)).foregroundStyle(.white)
                            Text("Dirancang & dibangun oleh Adnan.120hz.")
                                .font(.system(size: 12)).foregroundStyle(Color.white.opacity(0.55))
                        }
                        .padding(.horizontal, 16).padding(.vertical, 14)
                        divider
                        linkRow("GitHub", "github.com/adnan120hz", AppLinks.github)
                        divider
                        linkRow("Website", "adnan120hz.vercel.app", AppLinks.website)
                        divider
                        linkRow("TikTok", "@adnan.120hz", AppLinks.tiktok)
                    }

                    sectionTitle("LISENSI").padding(.top, 24)
                    card {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("© Adnan.120hz. Seluruh ikon dan elemen antarmuka dalam aplikasi ini digambar ulang dari nol — bukan aset Apple. iOS dan iPhone adalah merek dagang Apple Inc.; aplikasi ini adalah karya independen dan tidak berafiliasi maupun didukung oleh Apple.")
                            Text("Komponen platform yang digunakan: AVFoundation, SwiftUI, CoreImage dan Photos (Apple). Versi Android aplikasi ini memakai AndroidX CameraX, Jetpack Compose, Kotlin (Apache License 2.0) dan ML Kit (Google).")
                            Text(GlassStyle.description + ".")
                        }
                        .font(.system(size: 12))
                        .foregroundStyle(Color.white.opacity(0.75))
                        .padding(.horizontal, 16).padding(.vertical, 14)
                    }

                    sectionTitle("VERSI").padding(.top, 24)
                    card {
                        HStack {
                            Text("Kamera iOS").font(.system(size: 15)).foregroundStyle(.white)
                            Spacer()
                            Text(vm.appVersion)
                                .font(.system(size: 14)).foregroundStyle(Color.white.opacity(0.55))
                        }
                        .padding(.horizontal, 16).padding(.vertical, 14)
                        if let tag = vm.updateTag, let url = vm.updateURL {
                            divider
                            Button {
                                if let u = URL(string: url) { openURL(u) }
                            } label: {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("Update tersedia")
                                            .font(.system(size: 15, weight: .bold)).foregroundStyle(yellow)
                                        Text(tag)
                                            .font(.system(size: 12)).foregroundStyle(Color.white.opacity(0.5))
                                    }
                                    Spacer()
                                    Text("Unduh  ›")
                                        .font(.system(size: 13)).foregroundStyle(yellow)
                                }
                                .padding(.horizontal, 16).padding(.vertical, 13)
                            }
                            .buttonStyle(.plain)
                        }
                        divider
                        Button {
                            vm.reopenOnboarding()
                        } label: {
                            HStack {
                                Text("Perkenalan Developer")
                                    .font(.system(size: 15)).foregroundStyle(.white)
                                Spacer()
                                Text("Adnan.120hz")
                                    .font(.system(size: 13)).foregroundStyle(Color.white.opacity(0.5))
                                Text(" ›").foregroundStyle(Color.white.opacity(0.4))
                            }
                            .padding(.horizontal, 16).padding(.vertical, 13)
                        }
                        .buttonStyle(.plain)
                    }
                    Spacer().frame(height: 32)
                }
                .padding(.horizontal, 20)
                .padding(.top, 10)
            }
        }
    }

    private func sectionTitle(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 11, weight: .bold))
            .foregroundStyle(Color.white.opacity(0.45))
            .padding(.bottom, 8)
    }

    private func card<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(spacing: 0) { content() }
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(red: 0.11, green: 0.11, blue: 0.122))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private var divider: some View {
        Rectangle()
            .fill(Color.white.opacity(0.08))
            .frame(height: 1)
            .padding(.leading, 16)
    }

    private func linkRow(_ label: String, _ value: String, _ url: String) -> some View {
        Button {
            if let u = URL(string: url) { openURL(u) }
        } label: {
            HStack {
                Text(label).font(.system(size: 15)).foregroundStyle(.white)
                Spacer()
                Text(value).font(.system(size: 13)).foregroundStyle(Color.white.opacity(0.5))
                Text(" ›").foregroundStyle(Color.white.opacity(0.4))
            }
            .padding(.horizontal, 16).padding(.vertical, 13)
        }
        .buttonStyle(.plain)
    }
}
