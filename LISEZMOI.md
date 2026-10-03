# Wi-Fi Lunettes

App pour lunettes Rokid : affiche tes Wi-Fi connus à portée, tu cliques, ça se connecte.

## 1. Mettre le dépôt sur GitHub

Dépose tout le dossier sur un nouveau dépôt (de préférence **privé**).
Le dossier `.github/` se crée via **Add file → Create new file** si l'upload l'ignore.
Vérifie que `gradle/wrapper/gradle-wrapper.jar` est bien présent.

## 2. Ajouter tes Wi-Fi (secret `WIFI_LIST`)

**Settings → Secrets and variables → Actions → New repository secret**

- Name : `WIFI_LIST`
- Secret : un réseau par ligne, au format `NOM;motdepasse`

```
Maison;monMotDePasse123
iPhone de Kellian;codePartage
Freebox-ABC123;autreCode
WifiGratuit;
```

- Respecte exactement le nom du réseau (majuscules, espaces, tirets).
- Réseau sans mot de passe : rien après le `;`.
- Le mot de passe peut contenir des `;`, seul le premier sert de séparateur.

Les mots de passe restent dans le secret : ils ne sont jamais visibles dans le code du dépôt.

## 3. Compiler et installer

1. **Actions → build-apk → Run workflow**, attends la coche verte.
2. Télécharge l'artefact `wifi-lunettes-apk`, dézippe → `app-debug.apk`.
3. Installe via **Hi Rokid → installer un APK local**.
4. Au premier lancement, **accepte la permission de localisation** (obligatoire pour scanner les Wi-Fi).

## Utilisation

- En haut : le réseau actuellement connecté.
- La liste : tes réseaux, ceux à portée en premier avec le signal (▮▮▮▮).
- Glisse sur le pavé pour te déplacer, appuie pour **connecter**.
- Bouton **ACTUALISER** pour relancer un scan.

## Ligne de diagnostic (en bas de l'écran)

`Android X · liste : N · système : N · à portée : N`

- **système** : nombre de Wi-Fi déjà enregistrés dans les lunettes que l'app arrive à lire.
  Si c'est plus que 0, ils apparaissent aussi dans la liste (connexion sans mot de passe).
- **à portée : 0** alors qu'il y a du Wi-Fi : localisation refusée ou désactivée.

Envoie une photo de cette ligne à Claude après le premier test.

## Ajouter un réseau plus tard

Modifie le secret `WIFI_LIST` (**Update**), relance **Run workflow**, réinstalle l'APK.
