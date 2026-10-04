import SwiftUI

/// iOS-tick-style slider used by STYLES / EXPOSURE / CONFIG neighbours:
/// a row of ticks, the thumb line in yellow, the numeric value at right.
/// Dragging maps position directly to value (right = up) and the value
/// stays exactly where the finger leaves it — no spring ever fights.
struct TickSlider: View {
    let label: String
    @Binding var value: Double
    let range: ClosedRange<Double>
    var step: Double = 0
    var format: (Double) -> String = { String(format: "%.0f", $0) }

    private let yellow = Color(red: 1.0, green: 0.8, blue: 0.0)

    var body: some View {
        HStack(spacing: 10) {
            Text(label)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Color.white.opacity(0.85))
                .frame(width: 76, alignment: .leading)
            GeometryReader { geo in
                let fraction = CGFloat((value - range.lowerBound) / (range.upperBound - range.lowerBound))
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.white.opacity(0.18)).frame(height: 3)
                    HStack {
                        ForEach(0..<21, id: \.self) { _ in
                            Rectangle().fill(Color.white.opacity(0.45)).frame(width: 1, height: 7)
                            Spacer(minLength: 0)
                        }
                    }
                    Rectangle()
                        .fill(yellow)
                        .frame(width: 2.5, height: 20)
                        .offset(x: fraction * max(0, geo.size.width - 3))
                }
                .frame(height: 22)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { v in
                            let f = min(max(v.location.x / max(1, geo.size.width), 0), 1)
                            var nv = range.lowerBound + Double(f) * (range.upperBound - range.lowerBound)
                            if step > 0 { nv = (nv / step).rounded() * step }
                            value = min(max(nv, range.lowerBound), range.upperBound)
                        }
                )
            }
            .frame(height: 22)
            Text(format(value))
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(yellow)
                .frame(width: 46, alignment: .trailing)
        }
    }
}

struct SheetTile: Identifiable {
    let id: String
    let symbol: String
    let label: String
    let active: Bool
    let enabled: Bool
    let action: () -> Void
}

/// Bottom control sheet: a grid of round tiles (FLASH · LIVE · TIMER /
/// EXPOSURE · STYLES · FILTER / ASPECT · CONFIG · NIGHT / SETTINGS ·
/// GRID — one Column, one root, nothing ever overlaps), and tick-style
/// sub-panels behind them. Mirrors the Android 1.0+ sheet structure.
struct ControlSheet: View {
    @ObservedObject var vm: CameraViewModel
    @State private var dragY: CGFloat = 0
    private let yellow = Color(red: 1.0, green: 0.8, blue: 0.0)

    var body: some View {
        VStack(spacing: 0) {
            content
        }
        .padding(.horizontal, 16)
        .padding(.top, 10)
        .padding(.bottom, 18)
        .background(GlassPanelBackground(cornerRadius: 30))
        .offset(y: dragY)
        .gesture(
            DragGesture()
                .onChanged { v in dragY = max(0, v.translation.height) }
                .onEnded { v in
                    if v.translation.height > 90 { vm.sheet = .none }
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) { dragY = 0 }
                }
        )
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }

    @ViewBuilder
    private var content: some View {
        switch vm.sheet {
        case .grid: tileGrid
        case .flash: flashPanel
        case .timer: timerPanel
        case .exposure: exposurePanel
        case .styles: stylesPanel
        case .filter: filterPanel
        case .aspect: aspectPanel
        case .config: configPanel
        case .night: nightPanel
        case .action: actionPanel
        case .resolution: resolutionPanel
        case .none: EmptyView()
        }
    }

    // MARK: - Tile grid

    private var tiles: [SheetTile] {
        if vm.mode.isVideoFamily {
            return [
                SheetTile(id: "torch", symbol: "flashlight.on.fill", label: vm.facingFront ? "FLASH" : "TORCH",
                          active: vm.torchOn, enabled: vm.caps.hasTorch && !vm.facingFront) {
                    vm.setTorch(!vm.torchOn)
                    vm.showBanner(vm.torchOn ? "TORCH ON" : "TORCH OFF")
                },
                SheetTile(id: "action", symbol: "figure.run", label: "ACTION",
                          active: vm.actionOn, enabled: vm.mode == .video) {
                    if vm.mode == .video { vm.sheet = .action }
                    else { vm.showToast("ACTION (stabilisasi video) berlaku di mode VIDEO.") }
                },
                SheetTile(id: "resolution", symbol: "4k.tv", label: "RESOLUSI",
                          active: false, enabled: vm.mode == .video) {
                    if vm.mode == .video { vm.sheet = .resolution }
                    else { vm.showToast("Time-lapse dirangkai dari frame foto — resolusi mengikuti kamera foto.") }
                },
                SheetTile(id: "exposure", symbol: "plusminus", label: "EXPOSURE",
                          active: vm.focusBias != 0, enabled: true) { vm.sheet = .exposure },
                SheetTile(id: "styles", symbol: "paintpalette", label: "STYLES",
                          active: false, enabled: false) {
                    vm.showToast("Styles & filter berlaku untuk foto.")
                },
                SheetTile(id: "grid", symbol: "grid", label: "GRID",
                          active: vm.gridOn, enabled: true) {
                    vm.gridOn.toggle(); vm.persist()
                },
                SheetTile(id: "settings", symbol: "gearshape", label: "PENGATURAN",
                          active: false, enabled: true) {
                    vm.sheet = .none; vm.settingsOpen = true
                },
            ]
        }
        return [
            SheetTile(id: "flash", symbol: "bolt.fill", label: "FLASH",
                      active: vm.flash != .off, enabled: true) { vm.sheet = .flash },
            SheetTile(id: "live", symbol: "livephoto", label: "LIVE",
                      active: false, enabled: false) {
                vm.showToast("Live Photo belum tersedia di build ini.")
            },
            SheetTile(id: "timer", symbol: "timer", label: "TIMER",
                      active: vm.timerSec > 0, enabled: true) { vm.sheet = .timer },
            SheetTile(id: "exposure", symbol: "plusminus", label: "EXPOSURE",
                      active: vm.focusBias != 0, enabled: true) { vm.sheet = .exposure },
            SheetTile(id: "styles", symbol: "paintpalette", label: "STYLES",
                      active: vm.styleId != nil, enabled: true) { vm.sheet = .styles },
            SheetTile(id: "filter", symbol: "camera.filters", label: "FILTER",
                      active: vm.filterId != nil, enabled: true) { vm.sheet = .filter },
            SheetTile(id: "aspect", symbol: "aspectratio", label: "ASPECT",
                      active: vm.aspect != .ratio43, enabled: true) { vm.sheet = .aspect },
            SheetTile(id: "config", symbol: "slider.horizontal.3", label: "CONFIG",
                      active: !vm.config.isNeutral, enabled: true) { vm.sheet = .config },
            SheetTile(id: "night", symbol: "moon.fill", label: "NIGHT MODE",
                      active: vm.nightOn, enabled: true) { vm.sheet = .night },
            SheetTile(id: "settings", symbol: "gearshape", label: "PENGATURAN",
                      active: false, enabled: true) {
                vm.sheet = .none; vm.settingsOpen = true
            },
            SheetTile(id: "grid", symbol: "grid", label: "GRID",
                      active: vm.gridOn, enabled: true) {
                vm.gridOn.toggle(); vm.persist()
            },
        ]
    }

    private var tileGrid: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 3), spacing: 14) {
            ForEach(tiles) { tile in
                Button(action: tile.action) {
                    VStack(spacing: 6) {
                        ZStack {
                            Circle()
                                .fill(Color.black.opacity(0.45))
                                .frame(width: 62, height: 62)
                            Image(systemName: tile.symbol)
                                .font(.system(size: 23, weight: .medium))
                                .foregroundStyle(tile.active ? yellow : (tile.enabled ? .white : Color.white.opacity(0.3)))
                        }
                        Text(tile.label)
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundStyle(tile.enabled ? Color.white.opacity(0.85) : Color.white.opacity(0.3))
                    }
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("tile_\(tile.id)")
                .opacity(tile.enabled ? 1 : 0.75)
            }
        }
        .padding(.vertical, 6)
    }

    // MARK: - Sub-panels

    private func panelHeader(_ title: String) -> some View {
        HStack {
            Button {
                vm.sheet = .grid
            } label: {
                HStack(spacing: 2) {
                    Image(systemName: "chevron.left").font(.system(size: 12, weight: .bold))
                    Text("Kontrol").font(.system(size: 13, weight: .semibold))
                }
                .foregroundStyle(Color.white.opacity(0.8))
            }
            .buttonStyle(.plain)
            Spacer()
            Text(title)
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(Color.white.opacity(0.5))
        }
        .padding(.bottom, 8)
    }

    private func chip(_ label: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 12.5, weight: selected ? .bold : .semibold))
                .foregroundStyle(selected ? yellow : .white)
                .padding(.horizontal, 14)
                .frame(height: 34)
                .background(
                    Capsule().fill(Color.white.opacity(selected ? 0.22 : 0.10))
                        .overlay(Capsule().stroke(selected ? yellow : Color.clear, lineWidth: 1))
                )
        }
        .buttonStyle(.plain)
    }

    private var flashPanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            panelHeader("FLASH")
            if vm.mode.isVideoFamily {
                Text("Mode video memakai senter (torch) perangkat.")
                    .font(.system(size: 12)).foregroundStyle(Color.white.opacity(0.6))
                HStack(spacing: 8) {
                    chip("MATI", selected: !vm.torchOn) { vm.setTorch(false) }
                    chip("NYALA", selected: vm.torchOn) { vm.setTorch(true) }
                }
            } else {
                HStack(spacing: 8) {
                    ForEach(FlashSetting.allCases, id: \.self) { f in
                        chip(f.label, selected: vm.flash == f) {
                            vm.flash = f
                            vm.persist()
                            vm.showBanner("FLASH \(f.label)")
                        }
                    }
                }
            }
        }
    }

    private var timerPanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            panelHeader("TIMER")
            HStack(spacing: 8) {
                chip("OFF", selected: vm.timerSec == 0) { vm.timerSec = 0 }
                chip("3s", selected: vm.timerSec == 3) { vm.timerSec = 3 }
                chip("10s", selected: vm.timerSec == 10) { vm.timerSec = 10 }
            }
        }
    }

    private var exposurePanel: some View {
        VStack(alignment: .leading, spacing: 6) {
            panelHeader("EXPOSURE")
            TickSlider(
                label: "EXPOSURE",
                value: Binding(
                    get: { Double(vm.focusBias) },
                    set: { vm.setFocusBias(Float($0)) }
                ),
                range: Double(vm.engine.exposureBiasRange.lowerBound)...Double(vm.engine.exposureBiasRange.upperBound),
                step: 0.5
            ) { v in String(format: "%+.1f", v) }
            Text("Geser matahari di kotak fokus memberi efek yang sama, langsung dari preview.")
                .font(.system(size: 11)).foregroundStyle(Color.white.opacity(0.5))
        }
    }

    private var stylesPanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            panelHeader("STYLES")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(stylePresets) { preset in
                        chip(preset.label, selected: (vm.styleId ?? "standard") == preset.id) {
                            vm.styleId = preset.id == "standard" ? nil : preset.id
                            vm.persist()
                        }
                    }
                }
            }
            TickSlider(label: "INTENSITAS", value: $vm.styleIntensity, range: 0...1.5, step: 0.05) {
                String(format: "%.2f", $0)
            }
            TickSlider(label: "TONE", value: $vm.styleTone, range: -1...1, step: 0.1) {
                String(format: "%+.0f", $0 * 100)
            }
        }
    }

    private var filterPanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            panelHeader("FILTER")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(filterPresets) { preset in
                        chip(preset.label, selected: (vm.filterId ?? "none") == preset.id) {
                            vm.filterId = preset.id == "none" ? nil : preset.id
                            vm.persist()
                        }
                    }
                }
            }
            Text("Filter diterapkan nyata ke hasil foto lewat CoreImage.")
                .font(.system(size: 11)).foregroundStyle(Color.white.opacity(0.5))
        }
    }

    private var aspectPanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            panelHeader("ASPECT")
            HStack(spacing: 8) {
                ForEach(PhotoAspect.allCases, id: \.self) { a in
                    chip(a.label, selected: vm.aspect == a) {
                        vm.aspect = a
                        vm.persist()
                    }
                }
            }
        }
    }

    private var configPanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            panelHeader("CONFIG")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(photoConfigPresets) { preset in
                        chip(preset.label, selected: vm.configPresetId == preset.id) {
                            vm.applyConfigPreset(preset)
                        }
                    }
                    if vm.configPresetId == "custom" {
                        chip("KUSTOM", selected: true) {}
                    }
                }
            }
            configSlider("Ketajaman", value: $vm.config.sharpness, range: 0...1, format: { String(format: "%.0f", $0 * 100) })
            configSlider("Saturasi", value: $vm.config.saturation, range: 0.5...1.6, format: { String(format: "%.2f", $0) })
            configSlider("Kontras", value: $vm.config.contrast, range: 0.6...1.5, format: { String(format: "%.2f", $0) })
            configSlider("Gamma", value: $vm.config.gamma, range: 0.6...1.6, format: { String(format: "%.2f", $0) })
            configSlider("Reduksi Noise", value: $vm.config.denoise, range: 0...1, format: { String(format: "%.0f", $0 * 100) })
            HStack {
                Text("Config bawaan aplikasi ini — diterapkan nyata pada hasil foto.")
                    .font(.system(size: 11)).foregroundStyle(Color.white.opacity(0.5))
                Spacer()
                Button("Reset") {
                    vm.applyConfigPreset(photoConfigPresets[0])
                }
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(yellow)
            }
        }
    }

    private func configSlider(_ label: String, value: Binding<Double>,
                              range: ClosedRange<Double>, format: @escaping (Double) -> String) -> some View {
        HStack(spacing: 10) {
            Text(label)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Color.white.opacity(0.85))
                .frame(width: 96, alignment: .leading)
            Slider(value: Binding(
                get: { value.wrappedValue },
                set: { newValue in
                    value.wrappedValue = newValue
                    vm.markConfigCustom()
                    vm.persist()
                }
            ), in: range)
            .tint(yellow)
            Text(format(value.wrappedValue))
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(yellow)
                .frame(width: 40, alignment: .trailing)
        }
    }

    private var nightPanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            panelHeader("NIGHT MODE")
            HStack {
                Text("Night Mode")
                    .font(.system(size: 13, weight: .semibold)).foregroundStyle(.white)
                Spacer()
                Toggle("", isOn: Binding(
                    get: { vm.nightOn },
                    set: { on in
                        vm.nightOn = on
                        vm.engine.setLowLightBoost(on)
                        vm.showBanner(on ? "NIGHT MODE ON" : "NIGHT MODE OFF")
                    }
                ))
                .labelsHidden()
                .tint(Color(red: 0.2, green: 0.78, blue: 0.35))
            }
            Text(vm.engine.lowLightBoostSupported
                 ? "Mengaktifkan low-light boost AVFoundation untuk video; foto malam ditangani otomatis oleh pipeline perangkat."
                 : "Perangkat ini tidak menyediakan low-light boost; foto malam ditangani otomatis oleh pipeline perangkat.")
                .font(.system(size: 11)).foregroundStyle(Color.white.opacity(0.5))
        }
    }

    private var actionPanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            panelHeader("ACTION")
            HStack(spacing: 8) {
                chip("Nonaktif", selected: !vm.actionOn) { vm.setAction(false) }
                chip("Aktif", selected: vm.actionOn) { vm.setAction(true) }
            }
            HStack {
                Text("Stabilisasi (EIS sistem)")
                    .font(.system(size: 13, weight: .semibold)).foregroundStyle(.white)
                Spacer()
                Toggle("", isOn: Binding(
                    get: { vm.eisEnabled },
                    set: { vm.setEisEnabled($0) }
                ))
                .labelsHidden()
                .tint(Color(red: 0.2, green: 0.78, blue: 0.35))
            }
            Text(vm.stabSourceLabel)
                .font(.system(size: 12, weight: .semibold)).foregroundStyle(yellow)
            Text("Di iOS, stabilisasi video dikerjakan AVFoundation pada format aktif (sinematik/diperluas bila format mendukung). Tidak ada pipeline gyro software terpisah seperti di Android — sistemnya memang menyediakannya.")
                .font(.system(size: 11)).foregroundStyle(Color.white.opacity(0.5))
        }
    }

    private var resolutionPanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            panelHeader("RESOLUTION")
            HStack(spacing: 8) {
                ForEach(vm.caps.videoFormats) { fmt in
                    chip(fmt.label, selected: vm.selectedFormat?.id == fmt.id) {
                        vm.selectFormat(fmt)
                    }
                }
            }
            Text("FRAME RATE")
                .font(.system(size: 11, weight: .bold)).foregroundStyle(Color.white.opacity(0.5))
            HStack(spacing: 8) {
                ForEach(vm.selectedFormat?.fpsOptions ?? [30], id: \.self) { f in
                    chip("\(f)", selected: vm.fps == f) { vm.selectFps(f) }
                }
            }
        }
    }
}
