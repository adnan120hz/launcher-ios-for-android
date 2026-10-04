import SwiftUI

/// First-run developer introduction (shown once, re-openable from
/// Settings) — identical content and links to the Android onboarding.
struct OnboardingView: View {
    @ObservedObject var vm: CameraViewModel
    @Environment(\.openURL) private var openURL
    private let yellow = Color(red: 1.0, green: 0.8, blue: 0.0)

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VStack(spacing: 0) {
                Spacer().frame(height: 70)
                ZStack {
                    Circle().fill(Color.white.opacity(0.13)).frame(width: 110, height: 110)
                    Image(systemName: "camera.fill")
                        .font(.system(size: 44))
                        .foregroundStyle(.white)
                }
                Text("Kamera iOS")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(.top, 22)
                Text("Kamera bergaya iOS — dibangun dengan jujur mengikuti kemampuan nyata perangkatmu.")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.white.opacity(0.6))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 44)
                    .padding(.top, 8)

                Text("DEVELOPER")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Color.white.opacity(0.45))
                    .padding(.top, 30)
                Text("Adnan.120hz")
                    .font(.system(size: 21, weight: .bold))
                    .foregroundStyle(yellow)
                    .padding(.top, 4)

                VStack(spacing: 10) {
                    linkRow("TikTok", "@adnan.120hz", AppLinks.tiktok)
                    linkRow("Website", "adnan120hz.vercel.app", AppLinks.website)
                    linkRow("GitHub", "github.com/adnan120hz", AppLinks.github)
                }
                .padding(.horizontal, 20)
                .padding(.top, 20)

                Spacer()

                Button {
                    vm.finishOnboarding()
                } label: {
                    Text("Mulai")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(.black)
                        .frame(maxWidth: .infinity)
                        .frame(height: 54)
                        .background(Capsule().fill(yellow))
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 20)
                Text("Perkenalan ini hanya tampil sekali. Bisa dibuka lagi dari Pengaturan & Info.")
                    .font(.system(size: 11))
                    .foregroundStyle(Color.white.opacity(0.45))
                    .multilineTextAlignment(.center)
                    .padding(.top, 12)
                    .padding(.bottom, 26)
            }
        }
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
            .padding(.horizontal, 16)
            .frame(height: 52)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color.white.opacity(0.08))
            )
        }
        .buttonStyle(.plain)
    }
}
