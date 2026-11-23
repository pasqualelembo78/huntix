"""Encryption utilities for sensitive data.

Configurazione OBBLIGATORIA:
  - MASTER_KEY presente nell'ambiente;
  - libreria ``cryptography`` installata.

``validate_crypto_config()`` va chiamata all'avvio del backend: se manca uno
dei due prerequisiti il processo si ferma (fail-closed). Non esiste piu' il
fallback deterministico ``huntix-insecure-fallback-key`` e nessuna funzione
restituisce plaintext in caso di errore.

Compatibilita': i token cifrati in passato con la chiave derivata dalla costante
insecura non vengono piu' letti automaticamente. Possono essere recuperati solo
da una procedura di migrazione OFFLINE che chiami esplicitamente
``decrypt_with_legacy_key(ciphertext, chiave_vecchia_fornita_dall_operatore)``
e riscriva il valore con ``encrypt_value``. Vedere docs/AUDIT_TECNICO_HUNTIX.md.
"""

import os
import base64
import hashlib
import logging

logger = logging.getLogger(__name__)


class CryptoConfigurationError(RuntimeError):
    """Configurazione cifratura incompleta: il backend non deve partire."""


MAX_MASTER_KEY_CHARS = 64


def _get_master_key():
    key = os.environ.get("MASTER_KEY", "").strip()
    if not key:
        raise CryptoConfigurationError(
            "MASTER_KEY mancante: impostare una chiave segreta nell'ambiente "
            "prima di avviare il backend (nessun fallback insecure ammesso)"
        )
    if len(key) > MAX_MASTER_KEY_CHARS:
        # Storico: la chiave veniva troncata a 64 caratteri. Mantengo la stessa
        # regola per non cambiare la derivazione dei token gia' cifrati con
        # una MASTER_KEY lunga, ma la segnalo esplicitamente.
        logger.warning(
            "MASTER_KEY piu' lunga di %d caratteri: troncata per compatibilita' "
            "con i token gia' archiviati",
            MAX_MASTER_KEY_CHARS,
        )
    return key[:MAX_MASTER_KEY_CHARS]


def _import_fernet():
    try:
        from cryptography.fernet import Fernet, InvalidToken
    except ImportError as e:
        raise CryptoConfigurationError(
            "libreria 'cryptography' non installata: aggiungere il requisito "
            "backend/requirements.txt e reinstallare le dipendenze"
        ) from e
    return Fernet, InvalidToken


def _fernet_for(master_key):
    Fernet, _ = _import_fernet()
    # Stessa derivazione di sempre: sha256(master) in base64 url-safe (32 byte).
    fernet_key = base64.urlsafe_b64encode(hashlib.sha256(master_key.encode()).digest())
    return Fernet(fernet_key)


def validate_crypto_config():
    """Da chiamare all'avvio: solleva se la cifratura non e' utilizzabile."""
    _fernet_for(_get_master_key())


def encrypt_value(plaintext):
    """Cifra con MASTER_KEY. Solleva CryptoConfigurationError se non configurata."""
    if not plaintext:
        return ""
    cipher = _fernet_for(_get_master_key())
    try:
        return cipher.encrypt(plaintext.encode()).decode()
    except Exception as e:
        # Mai plaintext: in caso di errore il chiamante riceve una stringa vuota
        # e l'evento viene loggato senza il contenuto sensibile.
        logger.error("Encryption failed: %s", e)
        return ""


def decrypt_value(ciphertext):
    """Decritta con la MASTER_KEY corrente."""
    if not ciphertext:
        return ""
    return decrypt_with_master_key(ciphertext, _get_master_key())


def decrypt_with_master_key(ciphertext, master_key):
    """Decritta con una chiave passata esplicitamente (usata dalla migrazione)."""
    if not ciphertext:
        return ""
    if not master_key:
        raise CryptoConfigurationError("chiave di decrittazione vuota")
    _, InvalidToken = _import_fernet()
    cipher = _fernet_for(master_key)
    try:
        return cipher.decrypt(ciphertext.encode()).decode()
    except InvalidToken:
        logger.warning(
            "Decryption failed: token non valido o chiave diversa "
            "(valore cifrato non loggato)"
        )
        return ""
    except Exception as e:
        logger.error("Decryption failed: %s", e)
        return ""


def decrypt_with_legacy_key(ciphertext, legacy_master_key):
    """Solo migrazione OFFLINE: decritta con una chiave storica esplicita.

    Nuovo codice NON deve usarla: la chiave vecchia (inclusa quella derivata
    dalla costante insecure rimossa) puo' essere passata solo da un operatore
    che ha gia' eseguito il backup del database.
    """
    return decrypt_with_master_key(ciphertext, legacy_master_key)
