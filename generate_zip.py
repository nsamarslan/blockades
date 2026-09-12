import os
import shutil
import zipfile

# 1. Ensure .github/workflows exists inside SoruArsivi
workflow_dir = os.path.join("SoruArsivi", ".github", "workflows")
os.makedirs(workflow_dir, exist_ok=True)

workflow_yaml = """name: TRT Bil Bakalim Bot - APK Derle

on:
  push:
    branches: [ "**" ]
  pull_request:
    branches: [ "**" ]
  workflow_dispatch:

jobs:
  build:
    name: Debug APK Derle
    runs-on: ubuntu-latest

    steps:
      - name: Kodu Indir (Checkout)
        uses: actions/checkout@v4

      - name: Java 17 Kurulumu (Temurin)
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
          cache: 'gradle'

      - name: Proje Dizinini Duzenle ve Gradle Calistirma Izni Ver
        run: |
          # Eger repo icine SoruArsivi klasoru olarak pushlandiysa kok dizine tasi
          if [ -d "SoruArsivi" ] && [ ! -f "settings.gradle.kts" ]; then
            echo "Proje SoruArsivi alt klasorunde tespit edildi, kok dizine tasiniyor..."
            shopt -s dotglob
            mv SoruArsivi/* .
          fi
          if [ ! -f "gradlew" ]; then
            gradle wrapper
          fi
          chmod +x gradlew

      - name: APK Derle (assembleDebug)
        run: ./gradlew assembleDebug --stacktrace

      - name: APK Dosyasini Artifact Olarak Yukle
        uses: actions/upload-artifact@v4
        with:
          name: SoruArsivi-Bot-Debug-APK
          path: |
            app/build/outputs/apk/debug/*.apk
            **/build/outputs/apk/debug/*.apk
"""

with open(os.path.join(workflow_dir, "build-apk.yml"), "w", encoding="utf-8") as f:
    f.write(workflow_yaml)

# 2. Add gradlew and gradlew.bat
if os.path.exists("gradlew_template"):
    shutil.copy("gradlew_template", os.path.join("SoruArsivi", "gradlew"))
    os.chmod(os.path.join("SoruArsivi", "gradlew"), 0o755)

if os.path.exists("gradlew_bat_template"):
    shutil.copy("gradlew_bat_template", os.path.join("SoruArsivi", "gradlew.bat"))

wrapper_dir = os.path.join("SoruArsivi", "gradle", "wrapper")
os.makedirs(wrapper_dir, exist_ok=True)
if os.path.exists("gradle_wrapper_jar_template"):
    shutil.copy("gradle_wrapper_jar_template", os.path.join(wrapper_dir, "gradle-wrapper.jar"))

# 3. Add .gitignore
gitignore_content = """*.iml
.gradle
/local.properties
/.idea
.DS_Store
/build
/app/build
/captures
.externalNativeBuild
.cxx
local.properties
"""
with open(os.path.join("SoruArsivi", ".gitignore"), "w", encoding="utf-8") as f:
    f.write(gitignore_content)

# 4. Add README.md
readme_content = """# Soru Arşivi ve TRT Bil Bakalım Botu

Bu proje TRT Bil Bakalım için otomatik oynama, soru kaydetme ve erişilebilirlik botudur.

## 🚀 GitHub Actions ile Otomatik APK Oluşturma

1. Bu klasörün içindeki tüm dosyaları (özellikle `.github` klasörünü) GitHub reponuza pushlayın:
   ```bash
   git init
   git add .
   git commit -m "TRT Bil Bakalim Bot surumu"
   git branch -M main
   git remote add origin https://github.com/KULLANICI_ADINIZ/REPONUZ.git
   git push -u origin main
   ```
2. GitHub sayfanızda **Actions** sekmesine gidin.
3. **TRT Bil Bakalim Bot - APK Derle** iş akışı otomatik olarak başlayacaktır.
4. Derleme tamamlandığında (yaklaşık 2-3 dakika) en alttaki **Artifacts** bölümünden **`SoruArsivi-Bot-Debug-APK`** dosyasını tek tıkla telefonunuza indirebilirsiniz.
"""
with open(os.path.join("SoruArsivi", "README.md"), "w", encoding="utf-8") as f:
    f.write(readme_content)

# 5. Pack into public/SoruArsivi_Bot_Guncel.zip and public/SoruArsivi.zip
# We also create a ZIP where the files are at the root so when extracted, .github is right at the root!
os.makedirs("public", exist_ok=True)
zip_path = os.path.join("public", "SoruArsivi_Bot_Guncel.zip")

with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zipf:
    for root, dirs, filenames in os.walk("SoruArsivi"):
        for file in filenames:
            file_path = os.path.join(root, file)
            # Both relative to SoruArsivi parent, but let's see:
            # If we preserve "SoruArsivi/..." it's standard, and also if extracted inside repo:
            arcname = os.path.relpath(file_path, os.path.dirname("SoruArsivi"))
            zipf.write(file_path, arcname)

shutil.copy(zip_path, os.path.join("public", "SoruArsivi.zip"))
if os.path.exists("dist"):
    shutil.copy(zip_path, os.path.join("dist", "SoruArsivi_Bot_Guncel.zip"))
    shutil.copy(zip_path, os.path.join("dist", "SoruArsivi.zip"))

print(f"Zip created at {zip_path} with size {os.path.getsize(zip_path)} bytes")

# Also print listing
with zipfile.ZipFile(zip_path, "r") as z:
    for name in z.namelist():
        print("  ->", name)
