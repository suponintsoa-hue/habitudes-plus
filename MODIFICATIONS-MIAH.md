# Habitudes+ — version modifiée de Loop Habit Tracker

Basée sur [Loop Habit Tracker](https://github.com/iSoron/uhabits) d'Álinson S. Xavier (licence GPLv3, voir `LICENSE.txt`).
Modifications réalisées en novembre 2026 pour Miah.

## Ce qui a été ajouté

**Habitudes en durée (heures / minutes)**
- Nouveau type « Durée (heures et minutes) » à la création d'une habitude. Techniquement, c'est une habitude mesurable dont l'unité est `min`.
- La saisie accepte `1:30`, `1h30` ou `90`.
- La liste affiche `1h30` au lieu de `90`.
- Une carte « Totaux » sur chaque habitude affiche aujourd'hui, la semaine, le mois, l'année, le total et la moyenne par jour sur 30 jours. Elle marche aussi pour les habitudes Oui/Non (nombre de fois).

**Rappels en mode réveil**
- Case « Sonner comme un réveil » sous le rappel de chaque habitude.
- L'alarme s'affiche en plein écran, même sur l'écran verrouillé, et sonne en boucle avec vibration.
- Boutons : « C'est fait », « Rappeler dans 10 min » et « Arrêter ». Arrêt automatique après 5 minutes.

**Telegram** (menu ⋮ › Telegram & alarmes)
- Connexion d'un bot personnel (@BotFather). Bouton pour retrouver automatiquement le chat ID.
- Case « Envoyer aussi le rappel sur Telegram » par habitude, ou activation pour toutes les habitudes d'un coup.
- Bilan quotidien à l'heure choisie (habitudes faites ou non, valeurs, notes, temps total), avec en plus le bilan de la semaine le dernier jour de la semaine.
- Sauvegarde automatique de la base de données envoyée sur Telegram (chaque jour ou chaque semaine), ou à la demande.

**Pomodoro** (menu ⋮ › Pomodoro)
- Durées réglables pour le travail, la pause courte et la grande pause, ainsi que le nombre de sessions avant une grande pause.
- Chaque session terminée ajoute automatiquement ses minutes à l'habitude choisie (idéalement une habitude « Durée »).
- Notification sonore à la fin de chaque étape. Pause, reprise, passage à l'étape suivante. À l'arrêt, l'appli propose d'ajouter les minutes déjà faites.

**Autres changements**
- Identifiant de l'appli : `mg.miah.habitudes`. Elle s'installe à côté de Loop sans la remplacer.
- Nom affiché : « Habitudes+ ».
- Signature fixe (`uhabits-android/miah-release.keystore`) pour pouvoir installer les mises à jour par-dessus.
- Compilation automatique par GitHub Actions (`.github/workflows/build-apk.yml`) : l'APK est publié dans l'onglet *Releases*.

Le code ajouté se trouve principalement dans `uhabits-android/src/main/java/org/isoron/uhabits/miah/`.
