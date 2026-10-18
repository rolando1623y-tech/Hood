# Compilar PanaHoops FIT desde el celular con Termux y GitHub

Necesitas Android, una cuenta de GitHub y conexión a Internet. No publiques claves o contraseñas en chats ni repositorios.

1. Descarga el archivo `PanaHoops-FIT-3.1.1-fuente-actualizada.zip` en Descargas.
2. Instala Termux actualizado de una fuente oficial confiable.
3. Copia los siguientes comandos. En `git config user.email`, sustituye el ejemplo por tu dirección pública de commits o la dirección `noreply` indicada por tu cuenta de GitHub.

```bash
termux-setup-storage
pkg update -y
pkg install -y git gh unzip
mkdir -p "$HOME/panahoops-fit"
unzip -o "$HOME/storage/downloads/PanaHoops-FIT-3.1.1-fuente-actualizada.zip" -d "$HOME/panahoops-fit"
cd "$HOME/panahoops-fit/PanaHoops-Courtside"
ls -a
gh auth login -h github.com -p https -w

git init
git branch -M main
git config user.name "PanaHoops"
git config user.email "developer@example.com"
git add .
git commit -m "PanaHoops FIT 3.1.1 fuente"
gh repo create panahoops-fit --private --source=. --remote=origin --push
```

4. Abre `https://github.com/` en el navegador, entra al repositorio `panahoops-fit`, y ve a `Actions` → `PanaHoops FIT - compilar APK de prueba` → `Run workflow`.
5. Abre la ejecución. Si alguna prueba, lint o compilación falla, revisa el paso y corrige el código; **no hay APK verificada todavía**. Cuando termine correctamente, descarga `PanaHoops-FIT-debug-APK` de **Artifacts**.
6. Descomprime el ZIP de artefacto para obtener `app-debug.apk`. No intentes instalar el ZIP como APK.

**Actualizaciones:** si tienes Courtside 3.0 beta instalada, una nueva firma de debug de GitHub probablemente no coincidirá. **Exporta tu JSON primero**, conserva el APK anterior y no borres datos hasta que sepas que puedes restaurarlos. Una APK de prueba con certificado distinto no actualiza la aplicación previa sin desinstalar.
