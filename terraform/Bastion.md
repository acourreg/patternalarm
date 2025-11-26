# 🚀 Bastion SSH Fargate - Guide Simplifié

## ✨ Setup - Une Seule Commande

```bash
terraform apply
```

**C'est tout!** Terraform génère automatiquement:
- ✅ Clé SSH (RSA 4096 bits)
- ✅ Bastion Fargate toujours running
- ✅ Security Groups configurés
- ✅ Fichier `bastion-key.pem` dans ton dossier

---

## 🔌 Connexion au Bastion

### Étape 1: Récupérer l'IP du bastion

```bash
# Commande complète en une ligne
TASK_ARN=$(aws ecs list-tasks \
  --cluster patternalarm-cluster \
  --service-name patternalarm-bastion \
  --query 'taskArns[0]' --output text)

ENI_ID=$(aws ecs describe-tasks \
  --cluster patternalarm-cluster \
  --tasks $TASK_ARN \
  --query 'tasks[0].attachments[0].details[?name==`networkInterfaceId`].value' \
  --output text)

BASTION_IP=$(aws ec2 describe-network-interfaces \
  --network-interface-ids $ENI_ID \
  --query 'NetworkInterfaces[0].Association.PublicIp' \
  --output text)

echo "🎯 Bastion IP: $BASTION_IP"
```

### Étape 2: Créer le tunnel SSH

```bash
# Crée le tunnel (garde cette fenêtre ouverte)
ssh -i bastion-key.pem \
  -N \
  -L 5433:patternalarm-db.XXXXX.us-east-1.rds.amazonaws.com:5432 \
  ec2-user@$BASTION_IP \
  -p 2222
```

**Note:** Remplace `patternalarm-db.XXXXX...` par ton endpoint RDS (voir `terraform output rds_address`)

### Étape 3: Connecter à la base de données

```bash
# Dans une autre fenêtre terminal
psql -h localhost -p 5433 -U dbadmin -d patternalarm
```

---

## 📋 Script Helper (Optionnel)

Crée un script `connect-bastion.sh` pour simplifier:

```bash
#!/bin/bash
# connect-bastion.sh

set -e

CLUSTER="patternalarm-cluster"
SERVICE="patternalarm-bastion"
RDS_ENDPOINT="patternalarm-db.cyxw4kkgoup5.us-east-1.rds.amazonaws.com"  # ✅ Update with your RDS endpoint
KEY_FILE="bastion-key.pem"
LOCAL_PORT="5433"

echo "🔍 Getting bastion IP..."

TASK_ARN=$(aws ecs list-tasks --cluster $CLUSTER --service-name $SERVICE --query 'taskArns[0]' --output text)
ENI_ID=$(aws ecs describe-tasks --cluster $CLUSTER --tasks $TASK_ARN --query 'tasks[0].attachments[0].details[?name==`networkInterfaceId`].value' --output text)
BASTION_IP=$(aws ec2 describe-network-interfaces --network-interface-ids $ENI_ID --query 'NetworkInterfaces[0].Association.PublicIp' --output text)

echo "🎯 Bastion IP: $BASTION_IP"
echo ""
echo "🔒 Creating SSH tunnel..."
echo "   Local:  localhost:$LOCAL_PORT"
echo "   Remote: $RDS_ENDPOINT:5432"
echo ""
echo "💡 Press Ctrl+C to close the tunnel"
echo ""

ssh -i $KEY_FILE \
  -N \
  -L $LOCAL_PORT:$RDS_ENDPOINT:5432 \
  ec2-user@$BASTION_IP \
  -p 2222 \
  -o StrictHostKeyChecking=no \
  -o UserKnownHostsFile=/dev/null
```

**Utilisation:**
```bash
chmod +x connect-bastion.sh

# Terminal 1: Crée le tunnel
./connect-bastion.sh

# Terminal 2: Connecte-toi
psql -h localhost -p 5433 -U dbadmin -d patternalarm
```

---

## 🔒 Sécurité - Restreindre l'Accès SSH

Par défaut, le bastion accepte SSH depuis **n'importe où** (`0.0.0.0/0`).

### Restreindre à ton IP uniquement

1. **Obtenir ton IP:**
   ```bash
   curl ifconfig.me
   ```

2. **Modifier `ecs.tf` ligne ~240:**
   ```hcl
   resource "aws_security_group" "bastion" {
     # ...
     ingress {
       description = "SSH from my IP only"
       from_port   = 2222
       to_port     = 2222
       protocol    = "tcp"
       cidr_blocks = ["1.2.3.4/32"]  # ✅ Remplace par ton IP
     }
   }
   ```

3. **Appliquer:**
   ```bash
   terraform apply
   ```

---

## 💰 Coûts

| Service | Coût mensuel |
|---------|--------------|
| API Gateway | ~$4.50 |
| Flink Processor | ~$9 |
| Dashboard | ~$4.50 |
| **Bastion** | **~$7** |
| **Total** | **~$25/month** |

Le bastion tourne **24/7** pour être toujours disponible.

### Économiser $7/mois

Si tu veux économiser, change `desired_count = 0` dans `ecs.tf` et démarre manuellement:

```bash
# Start bastion quand nécessaire
aws ecs update-service \
  --cluster patternalarm-cluster \
  --service patternalarm-bastion \
  --desired-count 1

# Attendre 30s puis connecte-toi
sleep 30
./connect-bastion.sh

# Stop quand terminé
aws ecs update-service \
  --cluster patternalarm-cluster \
  --service patternalarm-bastion \
  --desired-count 0
```

**Coût on-demand:** ~$0.30/mois (1-2h/jour)

---

## 🛠️ Troubleshooting

### Problème: "Connection refused"

```bash
# Vérifier que le bastion tourne
aws ecs describe-services \
  --cluster patternalarm-cluster \
  --services patternalarm-bastion \
  --query 'services[0].runningCount'

# Devrait retourner: 1
```

### Problème: "Permission denied (publickey)"

```bash
# Vérifier les permissions de la clé
chmod 600 bastion-key.pem

# Tester la clé
ssh-keygen -y -f bastion-key.pem
```

### Problème: "Could not resolve hostname"

Le bastion n'a peut-être pas encore d'IP publique. Attendre 30 secondes puis réessayer.

### Problème: Clé perdue

```bash
# Regénérer la clé avec Terraform
terraform taint tls_private_key.bastion
terraform taint local_file.bastion_private_key
terraform apply

# ✅ Nouvelle clé générée: bastion-key.pem
```

---

## 📊 Comparaison des Approches

| Aspect | Version Actuelle | Version On-Demand |
|--------|------------------|-------------------|
| **Setup** | `terraform apply` | `terraform apply` + start/stop manuel |
| **Disponibilité** | Toujours prêt | Attente 30s au démarrage |
| **Coût** | $7/mois | $0.30-2/mois |
| **Complexité** | ⭐ Simple | ⭐⭐ Un peu plus |
| **Use case** | Accès quotidien | Accès occasionnel |

---

## 🎯 Résumé

### Démarrage Initial
```bash
terraform apply
# ✅ Bastion créé et running
```

### Usage Quotidien
```bash
# 1. Get IP
BASTION_IP=$(...)  # Voir script ci-dessus

# 2. Tunnel
ssh -i bastion-key.pem -N -L 5433:RDS_ENDPOINT:5432 ec2-user@$BASTION_IP -p 2222

# 3. Connect
psql -h localhost -p 5433 -U dbadmin -d patternalarm
```

### Avec Script Helper
```bash
# Terminal 1
./connect-bastion.sh

# Terminal 2
psql -h localhost -p 5433 -U dbadmin -d patternalarm
```

---

## 📝 Notes Importantes

1. **Clé privée:** `bastion-key.pem` est généré automatiquement
   - ⚠️ Ne jamais commit dans Git
   - ✅ Ajouter `*.pem` dans `.gitignore`

2. **Terraform state:** La clé est stockée dans `terraform.tfstate`
   - ✅ Utilise un backend S3 encrypté en prod
   - ⚠️ Protège l'accès au state file

3. **IP publique:** Change à chaque restart du bastion (rare)
   - Utilise le script pour récupérer l'IP à jour

4. **Sécurité:** Restreins l'accès SSH à ton IP pour plus de sécurité

---

**Questions?** Check les logs:
```bash
aws logs tail /ecs/patternalarm --follow --filter-pattern bastion
```
