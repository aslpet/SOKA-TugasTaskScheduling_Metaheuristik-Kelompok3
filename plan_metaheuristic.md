# Plan — Task Scheduling Metaheuristik: GSA vs TABWO (Kelompok 3)

Proyek `Heuristik` (CloudSim Plus) diubah dari HRRN/FCFS menjadi dua algoritma metaheuristik:

- **GSA** (Gravitational Search Algorithm), mengacu ke Mamalis & Perlitis, IJCA Vol. 184 No. 40, 2022.
- **TABWO** (Trust-Aware Black Widow Optimization), mengacu ke Wu, J. Eng. Appl. Sci. 71:139, 2024. Ini BWO ditambah prioritas task/VM, trust SLA, dan biaya listrik. Bukan BWO polos.

Dokumen ini adalah versi lengkap: verifikasi terhadap paper, dataset, model, spesifikasi algoritma, eksperimen, uji, fase, dan risiko. Status: **dataset selesai, implementasi belum dimulai**.

Istilah yang dipakai: "paper 1" = GSA, "paper 2" = TABWO, "asumsi" = hal yang tidak dijelaskan paper dan harus kita tetapkan sendiri (wajib ditulis di laporan).

---

## 1. Perubahan dari versi plan sebelumnya

1. Hasil verifikasi terhadap paper: versi lama **belum sesuai** (lihat §2). Fitness dan operator disesuaikan, TABWO sekarang memuat trust, prioritas, dan biaya listrik.
2. **Wave 1** memakai semua ukuran. **Wave 2 dan 3** memakai subset ukuran yang dipilih acak (§3).
3. Ditambah **Mode A** (setia paper, tiap algoritma memakai fitness sendiri) dan **Mode B** (fitness bersama, untuk membandingkan mekanisme pencarian secara adil) (§7).
4. Ditambah daftar uji verifikasi dari contoh angka di dokumen analisis paper (§10), traceability rumus ke kode, dan baseline pembanding.

---

## 2. Verifikasi plan terhadap model paper

Legenda status: ✅ sesuai, ⚠️ sesuai dengan asumsi (paper tidak menjelaskan), ❌ tidak sesuai di versi lama (sudah diperbaiki di plan ini).

### 2.1 GSA (paper 1)

| # | Komponen | Menurut paper | Plan lama | Status | Tindakan |
| --- | --- | --- | --- | --- | --- |
| G1 | Representasi | Agen = `[x¹..xᴷ]`, K = n task, tiap elemen menetapkan task ke VM (P1-7) | Vektor N dimensi, `floor` ke VM | ⚠️ | Pemetaan posisi kontinu ke indeks VM tidak dijelaskan paper; pakai `floor` + clipping |
| G2 | Fungsi fitness | `f = (γ·T_exec_max + ε1) / (δ·Rutil_sum + ε2)`, diminimalkan (P1-10) | `0,5·makespan/M₀ + 0,5·energy/E₀` | ❌ | Ganti ke bentuk paper (§5.2) |
| G3 | Waktu eksekusi | `T_exec_max = max_i Σ_j timeReq` (P1-8) | makespan | ⚠️ | Digeneralisasi ke model slot vCPU + arrival (§4) |
| G4 | Utilisasi | `Rutil_sum = Σ_i (Σ_j resourceReq / v_i)` (P1-9) | tidak ada | ❌ | Tambah, dengan pemetaan `resourceReq` dan `v_i` (§5.2) |
| G5 | Gaya total | Jumlah gaya dari **semua** agen lain `j ≠ i`, dikali `rand_j` (P1-2) | Kbest menurun linear | ❌ | Kbest bukan bagian paper. Pakai semua agen (SA = 30, murah) |
| G6 | Massa | `mᵢ = (fitᵢ − worst)/(best − worst) + ε`, `Mᵢ = mᵢ/Σmⱼ`, best = min (P1-3 s.d. P1-5) | tidak ditulis | ❌ | Tambah, termasuk penjaga `best = worst` |
| G7 | Massa pasif/aktif/inersia | Semua dianggap sama (`Mpᵢ = Maᵢ = Mᵢ = Miᵢ`) | tidak ditulis | ❌ | Tambah |
| G8 | Percepatan | `aᵢᵏ = Fᵢᵏ / Miᵢ` (P1-6) | tidak ditulis | ❌ | Tambah |
| G9 | Konstanta gravitasi | `G(t) = G₀(t₀/t_max)^φ` sebagaimana tercetak (konstan terhadap iterasi, nilai tidak diberikan) | `G₀ = 100`, `α = 20` | ⚠️ | Pakai bentuk GSA standar yang menurun: `G(t) = G₀·exp(−α·t/T)`, `G₀ = 100`, `α = 20`. Catat sebagai asumsi |
| G10 | Update kecepatan/posisi | Hanya ditulis "diperbarui", tanpa rumus | tidak ditulis | ⚠️ | Pakai GSA standar (Rashedi): `v(t+1) = rand·v(t) + a(t)`, `x(t+1) = x(t) + v(t+1)` |
| G11 | Jumlah agen SA, terminasi | Tidak disebutkan | pop 30, FE 3000 | ⚠️ | SA = 30; terminasi dengan batas jumlah evaluasi fitness (FE) |
| G12 | Konstanta fitness γ, δ, ε₁, ε₂ | Tidak diberi nilai; "dinormalisasi ke [0,1]" tanpa prosedur | tidak ada | ⚠️ | γ = 1/T₀, δ = 1/m, ε₁ = ε₂ = 1e-9 (§5.2) |
| G13 | Asumsi model | Task independen; kebutuhan sumber daya task sama di semua VM | dataset punya arrival dan deadline | ⚠️ | Task kita independen (✅). Arrival dan deadline adalah perluasan dari kita, ditulis sebagai asumsi |
| G14 | Metrik di paper | Makespan dan rata-rata utilisasi | makespan, energy, dll. | ✅ | Utilisasi sudah ada (`cpu_util_pct`), ditambah utilisasi per formula paper |
| G15 | Pembanding di paper | OLB, Min-Min, ESTA, improved PSO | FCFS/HRRN | ⚠️ | Tambah baseline OLB dan Min-Min (sederhana dan deterministik), plus FCFS/HRRN dari proyek lama |
| G16 | Skala | 16–1024 VM, 1000–50.000 task | 20 VM, 100–10.000 task | ⚠️ | Bukan replikasi eksperimen paper; arah tren (makespan naik, utilisasi naik seiring task) tetap bisa dibandingkan |

### 2.2 TABWO (paper 2)

| # | Komponen | Menurut paper | Plan lama | Status | Tindakan |
| --- | --- | --- | --- | --- | --- |
| T1 | Inisialisasi | `x = xₗ + rand(1,d) .* (xᵤ − xₗ)` (P2-13) | tidak ditulis | ❌ | Tambah: `xₗ = 0`, `xᵤ = m = 20`, decode `floor` |
| T2 | Procreation | `v₁ = αu₁ + (1−α)u₂`, `v₂ = αu₂ + (1−α)u₁`, α = array acak (P2-14) | disebut saja | ⚠️ | Pakai rumus paper. Pemilihan pasangan dan jumlah anak per pasangan mengikuti BWO asli [Hayyolalam & Kazem 2020] (cek ke paper aslinya) |
| T3 | Kanibalisme | Tiga jenis: betina memakan jantan, saudara, anak memakan induk | disebut saja | ⚠️ | Implementasi konkret di §6.3 (paper hanya menjelaskan konsep) |
| T4 | Mutasi adaptif | Tiga operator (single point, uniform, SBC); `mr = pₛ/L`; `pₛ = P + (t−1)(1−P)/(t_M−1)`; `P = L/50` (P2-15 s.d. P2-17) | `PM = 0,4` tetap | ❌ | Ganti ke skema adaptif; `L` tidak didefinisikan paper, asumsi `L = n` (panjang kromosom) |
| T5 | Parameter BWO (PP, CR, PM awal) | Tidak diberi nilai (mengacu ke BWO asli) | PP 0,6; CR 0,44; PM 0,4 | ⚠️ | Pakai nilai BWO asli (PP 0,6; CR 0,44; PM 0,4). Verifikasi ke paper asli sebelum dikunci |
| T6 | Trust SLA | `AV`, `SR`, `TE`, `trust = X₁AV + X₂SR + X₃TE` dengan X = 0,5/0,2/0,1 (P2-8 s.d. P2-11) | tidak ada | ❌ | **Bagian utama "TA" pada TABWO.** Tambah (§6.4) |
| T7 | Koreksi Persamaan 11 | Tercetak `X₂·SR × X₃·TE` (kemungkinan salah cetak) | – | ⚠️ | Dibaca sebagai penjumlahan |
| T8 | Prioritas task dan VM | `pr_v = pr_no × prMIPS`; `size = MIPS_req × pr`; `prio_task = size/pr_v`; `prio_VM = biaya listrik tertinggi / biaya DC` (P2-3 s.d. P2-7) | tidak ada | ❌ | Tambah (§6.5) |
| T9 | Biaya listrik per data center | Dipakai untuk prioritas VM | tidak ada | ❌ | Tambah harga listrik per DC sebagai asumsi (§6.5) |
| T10 | Beban VM dan host | `load = Σ load_v`; `load_host = load/host` (P2-1, P2-2) | load imbalance saja | ⚠️ | Dilaporkan sebagai metrik tambahan, tidak masuk fitness |
| T11 | Constraint eksekusi | Satu task per VM pada satu waktu | VM space-shared multi-vCPU | ⚠️ | Pertahankan model slot (1 vCPU = 1 unit eksekusi), ditulis sebagai asumsi (§4.1) |
| T12 | Execution queue berprioritas | Task diurutkan menurut prioritas, task tertunda tetap diprioritaskan | FCFS | ❌ | TABWO memakai antrean berprioritas (§6.5); GSA tetap FCFS |
| T13 | Fitness gabungan | **Tidak ada** di paper (makespan, energi, trust hanya disebut "terintegrasi") | makespan + energy | ⚠️ | Direkonstruksi sendiri (§6.6), wajib ditulis sebagai asumsi |
| T14 | Iterasi dan populasi | 50 iterasi, NP tidak disebut | pop 30, FE 3000 | ⚠️ | NP = 30; terminasi dengan FE yang sama dengan GSA |
| T15 | Metrik di paper | Makespan, energi, success rate, trust | tanpa success rate dan trust | ❌ | Tambah `success_rate_pct` dan `trust` ke hasil |
| T16 | Pembanding di paper | ACO, GA, PSO | – | ⚠️ | Tidak direplikasi; pembanding kita GSA, baseline, dan HRRN lama |
| T17 | Setelan simulasi paper | 5 DC, 30 VM, 100–1000 task, panjang task 800.000 | 2 DC, 20 VM, GoCJ | ⚠️ | Infrastruktur kita tetap dari draft proyek |

### 2.3 Kesimpulan verifikasi

Plan versi lama hanya benar pada kerangka umum (vektor solusi → VM, populasi, replay di CloudSim). Fitness GSA, aturan gaya GSA, operator mutasi, trust, prioritas, dan biaya listrik tidak sesuai atau belum ada. Semuanya sudah dimasukkan di bagian berikut.

Ada **tiga hal yang paper tidak jelaskan dan paling memengaruhi hasil**: pemetaan `resourceReq`/`v_i` untuk utilisasi GSA, fitness gabungan TABWO, dan arah pengurutan `prio_task`. Ketiganya ditandai sebagai asumsi dan masuk pilot (§11).

---

## 3. Dataset dan wave

**Generator:** `tools/generate_workload_meta.py`. **Keluaran:** `data/wave{1,2,3}/workload_{N}.csv` (32 file), `data/wave_manifest.csv` (urutan run), `data/dataset_summary.csv`, dan `data/pilot/workload_{500,2000}.csv` (khusus tuning).

### 3.1 Aturan wave

| Wave | Isi | Jumlah instance |
| --- | --- | --- |
| 1 | **Semua** ukuran: 100–1000 (step 100), 2000, 3000, 4000, 5000, 7500, 10000 | 16 |
| 2 | **Subset acak**: 5 dari 10 ukuran ≤ 1000 + 3 dari 6 ukuran > 1000 | 8 |
| 3 | **Subset acak independen** dengan aturan yang sama | 8 |

Hasil pemilihan (seed seleksi 2026, tercatat di `wave_manifest.csv`):

- Wave 1: 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 2000, 3000, 4000, 5000, 7500, 10000
- Wave 2: 100, 300, 600, 700, 900, 2000, 3000, 4000
- Wave 3: 200, 300, 600, 800, 1000, 3000, 5000, 10000

Pemilihan dibuat **bertingkat** (stratified): tanpa itu, subset acak murni berisiko tidak memuat ukuran besar, padahal ukuran besar adalah bagian paling berat dan paling menarik. Jumlah pilihan bisa diubah (`--k-small`, `--k-large`), dan pemilihan acak murni cukup dengan mengatur ulang aturan di generator.

Banyaknya sampel per ukuran (jumlah wave yang memuatnya): **3 sampel** untuk 300, 600, 3000; **2 sampel** untuk 100, 200, 700, 800, 900, 1000, 2000, 4000, 5000, 10000; **1 sampel** (hanya wave 1) untuk 400, 500, 7500. Konsekuensinya: standar deviasi hanya bermakna untuk ukuran dengan ≥ 2 sampel, dan jumlah sampel (`n`) wajib dicantumkan di setiap tabel hasil.

Total: 32 instance, ±3,3 MB untuk 32 CSV, ditambah 2 file pilot.

### 3.2 Cara data dibuat

- Workbook GoCJ hanya sampai 1000 cloudlet dan berisi `=RAND()`. Tabel Monte Carlo 50 baris di workbook (tiap baris peluang 0,02) dibaca, lalu diundi seragam dengan RNG ber-seed. Ini setara dengan `VLOOKUP(RAND(), B:C, 2)` di sheet GoCJ, tetapi berlaku untuk N berapa pun dan hasilnya beku.
- Kolom lain memakai aturan generator HRRN: arrival Poisson dengan offered load 2,0× kapasitas cluster (92.000 MIPS), deadline = arrival + 2–6× waktu di VM paling lambat, prioritas 1/2/3 (bobot 0,2/0,5/0,3), ukuran file/output per kelas, `ram_util` 0,05 atau 0,12.
- Skema kolom identik dengan proyek HRRN, sehingga `DatasetLoader` dan `TaskSpec` **tidak berubah**. Kolom `priority` (1 = tertinggi) dan `deadline` sudah tersedia untuk kebutuhan TABWO.
- **Seed:** `42 + 100000 × wave + N`. File wave 1 sama persis dengan versi data sebelumnya; wave 2 dan 3 sekarang hanya berisi subsetnya.
- **Data pilot:** `data/pilot/` berisi N = 500 dan 2000 dengan seed sendiri (`42 + 900000 + N`), berbeda dari semua file wave. Tuning parameter hanya boleh memakai data ini, supaya instance yang dilaporkan tidak ikut dipakai menyetel algoritma.
- Pengecekan: 32 file, jumlah baris = N, id berurutan, arrival menaik, deadline > arrival, manifest cocok dengan isi folder. Komposisi panjang task mengikuti GoCJ (±4% small, ±88% medium, ±8% large; 2% di antaranya 900.000 MI).

Membuat ulang:

```powershell
python tools/generate_workload_meta.py --xlsx GoCJ_DataSet_Monte_Carlo.xlsx
```

---

## 4. Model sistem dan evaluator

### 4.1 Infrastruktur dan asumsi eksekusi

Infrastruktur tidak berubah: 2 DC × 5 host (2 small, 2 medium, 1 large), 20 VM (12 small 2 vCPU 1000 MIPS, 6 medium 4 vCPU 1500 MIPS, 2 large 8 vCPU 2000 MIPS; 64 vCPU, kapasitas 92.000 MIPS).

Notasi: `m = 20` VM; VM `i` punya `sᵢ` vCPU, `μᵢ` MIPS per vCPU, kapasitas `Cᵢ = μᵢ·sᵢ`. Task `j`: panjang `Lⱼ` (MI), arrival `aⱼ`, deadline `dⱼ`, prioritas `pⱼ` ∈ {1,2,3}.

**Asumsi constraint "satu task per VM pada satu waktu" (paper 2) dan penjumlahan waktu sekuensial per VM (paper 1):** tiap **vCPU dianggap satu unit eksekusi non-preemptive**, jadi satu VM menjalankan paling banyak `sᵢ` task bersamaan. Ini dipertahankan agar sama dengan desain datacenter yang sudah dikumpulkan; keduanya paper mengasumsikan VM sebagai antrean tunggal, sehingga ini perlu ditulis sebagai penyesuaian di laporan. Model yang sama dipakai untuk kedua algoritma dan semua baseline.

### 4.2 Evaluator (tanpa CloudSim, dipakai di dalam optimasi)

Input: mapping `VM(j)` dan aturan urutan antrean. Proses per VM: task tiba pada `aⱼ`; ketika sebuah slot bebas, task yang sudah tiba dan menunggu dipilih menurut aturan urutan (FCFS atau prioritas); `start = max(aⱼ, waktu slot bebas)`; `finish = start + Lⱼ/μᵢ`. Kompleksitas O(N log N) per evaluasi (heap).

Keluaran per solusi: `startⱼ`, `finishⱼ`, waktu selesai VM `Fᵢ` (finish terakhir di VM i), beban kerja `Wᵢ = Σ Lⱼ` per VM dan per host, dan dari sana:

- `makespan M = maxᵢ Fᵢ`
- `energy` (Wh) dengan rumus `MetricsCalculator`: `Σ_host P_static × M + Σ_host (P_max − P_static) × W_host / kapasitas_host` (dibagi 3600). Pemetaan VM → host dihitung sekali dari alokasi CloudSim.
- jumlah task yang melewati deadline, dan statistik trust (§6.4).

### 4.3 Replay di CloudSim

Tiap solusi akhir di-replay satu kali: `cloudlet.setVm(vm)` dan `submissionDelay = start` hasil evaluator (cara yang sama dengan `Planner` HRRN). Metrik resmi diambil dari CloudSim lewat `MetricsCalculator`. Evaluator divalidasi terhadap CloudSim di Fase 1.

---

## 5. Spesifikasi GSA (paper 1)

### 5.1 Alur (Algoritma 1 paper)

```
inisialisasi SA agen Lᵢ (posisi acak seragam di [0, m))
ulangi sampai FE habis:
    untuk tiap agen: decode, evaluasi fitness
    best = min fit, worst = max fit
    hitung massa mᵢ, Mᵢ                        (P1-3 s.d. P1-5)
    hitung gaya total Fᵢᵏ dari semua agen j≠i  (P1-1, P1-2)
    hitung percepatan aᵢᵏ = Fᵢᵏ / Mᵢ           (P1-6)
    perbarui kecepatan dan posisi              (asumsi: GSA standar)
keluarkan agen terbaik
```

### 5.2 Fitness (Mode A)

- `T_exec_max = M` (makespan evaluator). Ini generalisasi P1-8 untuk model slot dan arrival.
- **Utilisasi (asumsi):** `resourceReq` task = panjang task `Lⱼ` (MI), dan `vᵢ` = kapasitas VM selama horizon jadwal `Cᵢ × T_exec_max`. Maka `Uᵢ = Wᵢ / (Cᵢ·T_exec_max)` dan `Rutil_sum = Σᵢ Uᵢ` (P1-9 dengan satuan yang konsisten, `0 ≤ Rutil_sum ≤ m`). Paper tidak menjelaskan satuan `resourceReq` dan `vᵢ`; pilihan ini membuat Rutil_sum sama maknanya dengan utilisasi CPU yang dilaporkan paper.
- `f = (γ·T_exec_max + ε₁) / (δ·Rutil_sum + ε₂)`, dengan `γ = 1/T₀` (T₀ = makespan mapping baseline OLB), `δ = 1/m`, `ε₁ = ε₂ = 1e-9`. Normalisasi ini mewakili kalimat paper "dinormalisasi ke [0,1]".

### 5.3 Parameter

| Parameter | Nilai | Sumber |
| --- | --- | --- |
| SA (jumlah agen) | 30 | asumsi |
| Gaya | semua agen (`j ≠ i`), faktor `rand_j` | paper (P1-2) |
| `G(t)` | `G₀·exp(−α·t/T)`, `G₀ = 100`, `α = 20` | asumsi (bentuk tercetak paper konstan dan tanpa nilai) |
| `ε` massa dan jarak | 1e-12 | asumsi |
| Posisi → VM | `floor`, clip ke `[0, m−1]`, posisi dibatasi di `[0, m)` | asumsi |
| Terminasi | FE = 3000 (tentatif, §7.3) | asumsi |

---

## 6. Spesifikasi TABWO (paper 2)

### 6.1 Alur

```
hitung prioritas VM dan task                         (P2-3 s.d. P2-7)
inisialisasi NP spider                               (P2-12, P2-13)
ulangi sampai FE habis:
    procreation: pasang spider acak, buat anak       (P2-14)
    cannibalism: sexual, sibling, child-eats-mother
    mutasi adaptif dengan laju mr(t)                 (P2-15 s.d. P2-17)
    evaluasi: makespan, biaya energi, trust          (§6.6)
keluarkan spider terbaik; jalankan dengan antrean berprioritas
```

### 6.2 Inisialisasi dan procreation

- Inisialisasi (P2-13): `x = xₗ + rand(1,d)·(xᵤ − xₗ)`, `xₗ = 0`, `xᵤ = m`, `d = n`. Pada Mode A, sebagian spider awal diundi dengan peluang VM ∝ `prio_VMᵢ × Cᵢ` (cara memakai prioritas VM, asumsi); sisanya seragam.
- Procreation (P2-14): untuk pasangan `(u₁, u₂)` dibuat `α` acak ber-dimensi `d`, lalu `v₁ = αu₁ + (1−α)u₂`, `v₂ = αu₂ + (1−α)u₁` (kombinasi konveks). Pasangan dan jumlah anak per pasangan mengikuti BWO asli.

### 6.3 Cannibalism

Paper hanya menjelaskan konsepnya. Implementasi yang dipilih:

- **Sexual:** setelah kawin, orang tua dengan fitness lebih buruk (jantan) dihapus dari kumpulan orang tua.
- **Sibling:** dari seluruh anak, hanya fraksi `(1 − CR)` terbaik yang bertahan (`CR = 0,44`).
- **Child eats mother:** jika seekor anak lebih baik dari induk betina, induk diganti anak tersebut.

### 6.4 Mutasi adaptif

- `mr = pₛ / L`, `pₛ = P + (t − 1)(1 − P)/(t_M − 1)`, `P = L/50` (P2-15 s.d. P2-17).
- **Asumsi:** `L = n` (panjang kromosom, tidak didefinisikan paper). Contoh: n = 100 → mr turun 0,02 → 0,01; n = 10.000 → mr turun 0,02 → 0,0001.
- `t_M` = jumlah generasi maksimum, diperkirakan dari FE ÷ evaluasi per generasi.
- Operator mutasi dipilih acak (peluang sama) dari **single point**, **uniform**, dan **SBC** (η = 20, asumsi). `mr` dipakai sebagai peluang perubahan per gen. Detail penerapan tiga operator pada mutasi tidak dijelaskan paper.

### 6.5 Trust, prioritas, dan biaya listrik

**Trust per VM `i`** (P2-8 s.d. P2-11):

| Simbol | Definisi di simulasi | Catatan |
| --- | --- | --- |
| `AV_i` | task diterima VM ÷ task ditugaskan ke VM | selalu 1 di simulator (tidak ada penolakan atau kegagalan VM); disimpan sebagai titik ekstensi injeksi kegagalan |
| `SR_i` | task selesai **sebelum deadline** ÷ task ditugaskan | sesuai definisi paper (berhasil dan memenuhi SLA) |
| `TE_i` | rata-rata `min(1, ESTT_j / ACTT_j)` | `ESTT = Lⱼ/μᵢ` (janji tanpa antre), `ACTT = finish − arrival`; dibatasi 1 karena paper tidak membatasi |
| `trust_i` | `X₁·AV + X₂·SR + X₃·TE`, X = 0,5 / 0,2 / 0,1 | bentuk penjumlahan (koreksi P2-11); nilai maksimum 0,8 |

Trust sistem = rata-rata `trust_i` berbobot jumlah task per VM, dinormalisasi `/ (X₁+X₂+X₃)` agar berada di [0,1].

**Prioritas dan biaya listrik:**

- `pr_v = pr_no × prMIPS` (P2-3), dibaca sebagai (jumlah vCPU) × (MIPS per vCPU) = `Cᵢ`. Bacaan ini cocok dengan contoh paper (1050 MIPS per PE).
- `size_j = L_j × w_j`, `w_j = 4 − pⱼ` (prioritas 1 = bobot 3). Pembalikan skala karena dataset kita memakai 1 = tertinggi (P2-5).
- `prio_task_j = size_j / pr_v` (P2-6). **Urutan antrean: nilai terbesar dijalankan lebih dulu**, tie → arrival lebih awal. Paper tidak menyebut arah pengurutan; arah sebaliknya diuji di pilot (§11).
- `prio_VM_i = harga listrik tertinggi / harga listrik DC` (P2-7). Harga (asumsi): DC0 = 1,0, DC1 = 1,5 (satuan relatif). VM di DC0 berprioritas 1,5; di DC1 1,0.
- Biaya energi `E_cost = Σ_DC harga_DC × energi_DC (kWh)`.
- `ToT` (P2-4) dan beban host (P2-1, P2-2) tidak masuk fitness; beban host hanya dilaporkan.

### 6.6 Fitness TABWO (Mode A, rekonstruksi)

`F = w_M · M/M₀ + w_E · E_cost/E₀ + w_T · (1 − Trust_norm)`

dengan `(w_M, w_E, w_T) = (0,4; 0,3; 0,3)` awal (diuji di pilot), `M₀` dan `E₀` dari mapping baseline OLB pada workload yang sama. Ini sesuai tujuan paper (makespan dan energi turun, trust naik), tetapi **bentuk gabungan ini rekonstruksi kita**.

### 6.7 Parameter

| Parameter | Nilai | Sumber |
| --- | --- | --- |
| NP | 30 | asumsi |
| PP / CR / PM awal | 0,6 / 0,44 / 0,4 | BWO asli (cek ke paper aslinya) |
| `xₗ, xᵤ` | 0, m | asumsi |
| `L`, `P`, `t_M` | n, n/50, FE ÷ evaluasi per generasi | `P` dari paper, sisanya asumsi |
| Trust X₁, X₂, X₃ | 0,5 / 0,2 / 0,1 | paper (contoh) |
| Harga listrik DC0 / DC1 | 1,0 / 1,5 | asumsi |
| Bobot fitness | 0,4 / 0,3 / 0,3 | asumsi |
| η (SBC) | 20 | asumsi |

---

## 7. Desain eksperimen

### 7.1 Dua mode

| | **Mode A — setia paper** | **Mode B — fitness bersama** |
| --- | --- | --- |
| Tujuan | Menguji tiap algoritma seperti diusulkan paper | Membandingkan mekanisme pencarian saja |
| Fitness | GSA: P1-10 (§5.2); TABWO: §6.6 | Keduanya: `0,5·M/M₀ + 0,5·E/E₀` (draft proyek) |
| Urutan antrean | GSA: FCFS; TABWO: berprioritas | FCFS untuk keduanya |
| Inisialisasi | GSA: acak; TABWO: acak + bias `prio_VM` | acak seragam untuk keduanya |
| Peran | Hasil utama laporan | Kontrol pembanding yang adil |

Alasannya: pada Mode A, selisih GSA vs TABWO bercampur antara efek algoritma dan efek fungsi tujuan yang berbeda. Mode B memisahkan keduanya. Jika waktu terbatas, Mode A diprioritaskan.

### 7.2 Jumlah run

| Komponen | Jumlah |
| --- | --- |
| Instance (wave 1+2+3) | 16 + 8 + 8 = **32** |
| Run metaheuristik per mode | 32 × 2 algoritma = **64** |
| Total (Mode A + B) | **128** |
| Baseline deterministik (OLB, Min-Min, FCFS, HRRN) | 32 × 4 = 128 (cepat) |

Urutan: wave 1 → wave 2 → wave 3 berturut-turut; di dalam wave menurut `wave_manifest.csv` (ukuran menaik). Seed algoritma = `1000 × wave + N` (dicatat di CSV hasil).

### 7.3 Anggaran evaluasi

Keadilan dijaga dengan membatasi **jumlah evaluasi fitness (FE)** yang sama untuk kedua algoritma (BWO mengevaluasi jumlah anak yang berbeda tiap generasi, GSA tepat NP per iterasi). Nilai awal: NP = 30, FE = 3000. Karena pada N = 10.000 anggaran ini sangat kecil terhadap dimensi (lihat §12), pilihan akhir ditentukan di pilot: FE tetap, atau `FE = 3000 + N`. Apa pun pilihannya, sama untuk semua algoritma.

### 7.4 Metrik

Dari CloudSim (proyek lama): makespan, energy, avg execution/response/turnaround time, CPU utilization, throughput, load imbalance, deadline missed.

Tambahan: `success_rate_pct` (= task yang memenuhi deadline, metrik paper 2), `trust` (§6.5), `rutil_sum` (§5.2, metrik paper 1), `energy_cost` (§6.5), `sched_time_ms` (waktu komputasi optimasi), `fe_used`, `final_fitness`, dan kurva konvergensi (best fitness per generasi) per run.

### 7.5 Agregasi dan analisis

1. **Rata-rata per ukuran antar wave** (`summary_avg.csv`): tiap metrik per (mode, algoritma, ukuran) dengan rata-rata, std (bila n ≥ 2), dan `n`.
2. **Ringkasan per wave** (`summary_wave.csv`): karena set ukuran tiap wave berbeda, nilai mentah tidak bisa langsung dirata-ratakan antar wave (skalanya berbeda hingga ratusan kali). Dipakai selisih relatif terhadap baseline OLB per instance (%), lalu dirata-ratakan per wave. Dengan cara ini wave 1, 2, dan 3 tetap sebanding.
3. **Uji berpasangan:** Wilcoxon signed-rank GSA vs TABWO pada 32 pasangan instance, per metrik utama (makespan, energy, utilization, success rate).
4. **Grafik:** metrik vs ukuran workload, kurva konvergensi (beberapa ukuran), waktu komputasi vs N, perbandingan Mode A vs Mode B.

---

## 8. Perubahan kode

| File | Keterangan |
| --- | --- |
| `Scheduler` (baru) | Interface: `int[] map(...)` + aturan urutan antrean |
| `ScheduleEvaluator` (baru) | Evaluator §4.2, memuat FCFS dan antrean berprioritas |
| `GsaFitness`, `TabwoFitness`, `CommonFitness` (baru) | Fitness §5.2, §6.6, dan Mode B |
| `Gsa` (baru) | §5, mencatat kurva konvergensi |
| `Tabwo` (baru) | §6: procreation, cannibalism, mutasi adaptif |
| `TrustModel`, `PriorityModel` (baru) | P2-8 s.d. P2-11 dan P2-3 s.d. P2-7 |
| `Baselines` (baru) | OLB, Min-Min (dan memakai `Planner` untuk FCFS/HRRN) |
| `ExperimentRunner` (baru) | Membaca `wave_manifest.csv`, menjalankan semua wave dan mode, menulis hasil |
| `Main` | Argumen `--mode A,B --algos gsa,tabwo --waves 1,2,3`; replay mapping ke CloudSim |
| `MetricsCalculator` | Tambah kolom (§7.4) |
| `tools/aggregate_results.py` (baru) | §7.5 |
| `config/params.properties` (baru) | Semua parameter di §5.3 dan §6.7, supaya mudah dikunci dan dicatat |

`DatasetLoader`, `TaskSpec`, `DatacenterFactory`: tidak berubah.

Berkas hasil: `results/results_meta.csv`, `results/convergence/`, `results/summary_avg.csv`, `results/summary_wave.csv`.

---

## 9. Struktur repo

```
Heuristik/
├── pom.xml
├── config/params.properties
├── data/
│   ├── wave_manifest.csv
│   ├── dataset_summary.csv
│   └── wave{1,2,3}/workload_{N}.csv
├── tools/generate_workload_meta.py, aggregate_results.py
├── src/main/java/   (file lama + file baru §8)
├── src/test/java/   (uji §10)
└── results/
```

---

## 10. Uji verifikasi (traceability ke paper)

Contoh angka berikut berasal dari dokumen analisis paper (angkanya buatan penyusun dokumen, bukan hasil paper), tetapi cukup untuk memeriksa bahwa rumus terimplementasi benar.

| Uji | Input | Hasil yang diharapkan | Rumus |
| --- | --- | --- | --- |
| Fitness GSA | `T_max = 11`, `Rutil_sum = 1,45`, γ = δ = 1, ε = 0 | `f = 7,586` | P1-10 |
| Fitness GSA | `T_max = 7`, `Rutil_sum = 1,65` | `f = 4,242` | P1-10 |
| Massa GSA | dua agen, best 4,242 dan worst 7,586 | `M_B ≈ 1`, `M_A ≈ 0` | P1-3 s.d. P1-5 |
| Procreation | `u₁ = [2,5,1,4]`, `u₂ = [3,1,6,2]`, `α = [0,5; 0,2; 0,8; 0,5]` | `v₁ = [2,5; 1,8; 2,0; 3,0]`, `v₂ = [2,5; 4,2; 5,0; 3,0]`; `v₁ + v₂ = u₁ + u₂` | P2-14 |
| Laju mutasi | L = 100, `t_M` = 50 | t = 1: 0,020; t = 25: ≈ 0,0151; t = 50: 0,010 | P2-15 s.d. P2-17 |
| Prioritas VM | harga listrik 0,10 / 0,15 / 0,20 | 2,00 / 1,33 / 1,00 | P2-7 |
| Prioritas task | `pr_no = 2`, `prMIPS = 1050`, `mMIPS = 500`, `mpr = 2` | `size = 1000`, `prio = 0,476` | P2-3, 5, 6 |
| Trust | AV = 0,9; SR = 0,9; TE = 0,8 | `0,71` (bentuk penjumlahan) | P2-11 |

Uji lain:

- **Evaluator vs CloudSim:** mapping acak pada N = 100 dan 500; selisih makespan dan energy prediksi vs CloudSim < 1%.
- **Kewajaran:** fitness akhir GSA dan TABWO lebih baik dari mapping acak dan OLB pada N = 100 dan 500.
- **Determinisme:** seed yang sama menghasilkan solusi identik.
- **Integritas:** jumlah cloudlet selesai = jumlah task di setiap run.

---

## 11. Fase pengerjaan

### Fase 0 — Dataset (selesai)
- [x] 32 CSV (wave 1 penuh, wave 2 dan 3 subset acak), 2 CSV pilot, manifest, ringkasan, generator

### Fase 1 — Kerangka dan evaluator
- [ ] `Scheduler`, `ScheduleEvaluator`, `config/params.properties`
- [ ] Validasi evaluator terhadap CloudSim (§10)
- **Selesai jika:** selisih < 1% dan penyebab sisa selisih bisa dijelaskan

### Fase 2 — Baseline
- [ ] OLB dan Min-Min; hitung `T₀`, `M₀`, `E₀` untuk normalisasi
- **Selesai jika:** hasil baseline per instance tersimpan

### Fase 3 — GSA
- [ ] `GsaFitness` dan `Gsa`; lulus uji P1 (§10)
- **Selesai jika:** fitness akhir lebih baik dari acak dan OLB pada N = 100 dan 500

### Fase 4 — TABWO
- [ ] `TrustModel`, `PriorityModel`, `Tabwo`; lulus uji P2 (§10)
- **Selesai jika:** syarat kewajaran sama dengan GSA, `trust` dan `success_rate` terhitung

### Fase 5 — Pilot dan penguncian parameter
- [ ] Hanya memakai `data/pilot/` (N = 500 dan 2000), **bukan** file wave
- [ ] Putuskan: FE tetap vs `3000 + N`; arah pengurutan `prio_task`; bobot fitness TABWO; G₀ dan α untuk GSA
- [ ] **Bekukan parameter** di `config/params.properties`, jangan diubah setelah ini
- [ ] Ukur waktu komputasi di N = 10.000

### Fase 6 — Eksperimen penuh
- [ ] Mode A: wave 1 → 2 → 3 (64 run); lalu Mode B (64 run); baseline (128 run)
- **Selesai jika:** semua baris ada di `results_meta.csv` tanpa WARNING cloudlet hilang

### Fase 7 — Agregasi dan analisis
- [ ] `summary_avg.csv`, `summary_wave.csv`, uji Wilcoxon, grafik (§7.5)
- [ ] Bandingkan arah tren dengan paper (makespan naik, utilisasi naik seiring task), tanpa mengklaim replikasi angka

### Fase 8 — Real world (bila tetap diwajibkan di tugas)
- [ ] Docker/VM sebagai worker, scheduler memakai mapping dari optimasi; 10.000 task memerlukan faktor skala waktu yang konsisten. Konfirmasi dulu ke asdos apakah diterima dan apakah memakai ukuran penuh

---

## 12. Risiko dan mitigasi

| Risiko | Mitigasi |
| --- | --- |
| **Dimensi besar vs anggaran kecil:** N = 10.000 dimensi dengan 3000 evaluasi hampir pasti konvergen buruk, bahkan bisa kalah dari OLB atau Min-Min | Putuskan skema FE di pilot (§7.3). Opsional inisialisasi sebagian populasi dengan solusi greedy, sama untuk kedua algoritma dan ditulis di laporan |
| Fitness berbeda membuat GSA vs TABWO tidak adil | Mode B sebagai kontrol; Mode A dan B dilaporkan berdampingan |
| Fitness TABWO adalah rekonstruksi (paper tidak memberi rumus) | Dinyatakan jelas sebagai asumsi; sensitivitas bobot diuji di pilot |
| Trust degenerate (AV selalu 1 karena tidak ada kegagalan) | Diterima dan dilaporkan; injeksi kegagalan VM disebut sebagai pengembangan lanjutan |
| Arah pengurutan `prio_task` tidak jelas dan bisa menguntungkan task panjang | Uji kedua arah di pilot, kunci satu, tulis alasannya |
| Pemetaan utilisasi GSA (`resourceReq`, `vᵢ`) adalah interpretasi | Ditulis sebagai asumsi; laporkan juga utilisasi CPU dari CloudSim sebagai pembanding independen |
| Paper punya notasi dan rumus tercetak yang ambigu (P1-1 `d` vs `k`, `G(t)`, P2-11, `L`, `ToT`) | Semua keputusan ada di §2 dan §5–6; satu tempat acuan untuk laporan |
| Evaluator tidak sama dengan CloudSim | Validasi di Fase 1; urutan antrean per VM harus identik |
| Waktu komputasi: gaya GSA O(SA² × N) per iterasi, ≈ 8,7 juta operasi gaya per iterasi untuk N = 10.000 (SA = 30, 29 agen lain) | Diukur di pilot. Estimasi kasar puluhan detik per run, masih layak untuk 128 run |
| CloudSim berat untuk 10.000 cloudlet | Replay hanya 1× per run; naikkan heap JVM (`-Xmx`) bila perlu |
| Sampel per ukuran sedikit (1–3) karena wave 2 dan 3 subset | `n` selalu dicantumkan; kesimpulan utama bersandar pada uji berpasangan 32 instance, bukan std per ukuran |
| Parameter BWO asli (PP, CR, PM) berasal dari ingatan terhadap paper aslinya | Cek ke Hayyolalam & Kazem (2020) sebelum dikunci |

---

## 13. Keputusan yang masih terbuka

1. **Wave 2 dan 3:** 8 instance per wave dengan seleksi bertingkat (5 kecil + 3 besar) sudah cocok, atau mau jumlah/aturan lain (misal acak murni)?
2. **Mode B** (fitness bersama) tetap dikerjakan, atau cukup Mode A saja?
3. Interpretasi tiga hal yang paper tidak jelaskan (utilisasi GSA, fitness gabungan TABWO, arah `prio_task`) disetujui untuk dipakai sebagai default?
4. Harga listrik antar-DC (1,0 dan 1,5) cukup sebagai asumsi, atau ada angka dari tugas?
5. Baseline OLB dan Min-Min ditambahkan (sesuai pembanding paper 1)? FCFS/HRRN lama tetap disertakan?
6. Bagian real world tetap dikerjakan dengan algoritma baru?
