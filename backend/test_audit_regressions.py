"""Test di regressione dell'audit tecnico 23/11/2026.

Eseguibili senza server, database, rete o dati reali:

    cd backend && ./venv/bin/python -m unittest -v test_audit_regressions

Coprono due difetti confermati dal sorgente:
  1. logout: blacklist scritta come stringa ma letta come tupla (jti, exp);
  2. crypto: fallback deterministico e plaintext quando manca cryptography.
"""

import os
import sys
import time
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import auth_fastapi                      # noqa: E402
from storage import crypto as crypto_mod  # noqa: E402


class TestTokenBlacklistRegression(unittest.TestCase):
    def setUp(self):
        auth_fastapi._token_blacklist.clear()
        patcher = mock.patch.object(
            auth_fastapi, "_get_jwt_secret", return_value="secret-di-test"
        )
        patcher.start()
        self.addCleanup(patcher.stop)
        self.addCleanup(auth_fastapi._token_blacklist.clear)

    def test_vecchio_comportamento_non_revocava(self):
        """Riproduzione del bug: una stringa jti non viene mai riconosciuta."""
        payload = {
            "user_id": "u-test",
            "jti": "jti-di-test-1234",
            "exp": time.time() + 900,
        }
        token = auth_fastapi._create_jwt(payload)
        self.assertIsNotNone(auth_fastapi._verify_jwt(token))

        # Forma scritta dal vecchio logout: _token_blacklist.add(jti)
        auth_fastapi._token_blacklist.add(payload["jti"])
        self.assertIsNotNone(
            auth_fastapi._verify_jwt(token),
            "il bug storico doveva lasciare il token valido",
        )

    def test_helper_revoca_access_token(self):
        payload = {
            "user_id": "u-test",
            "jti": "jti-di-test-5678",
            "exp": time.time() + 900,
        }
        token = auth_fastapi._create_jwt(payload)
        self.assertIsNotNone(auth_fastapi._verify_jwt(token))

        auth_fastapi.blacklist_access_token(payload)
        self.assertIsNone(auth_fastapi._verify_jwt(token))

    def test_logout_usa_helper_e_non_scrive_piu_stringhe(self):
        path = os.path.join(
            os.path.dirname(os.path.abspath(__file__)), "app_routes", "auth.py"
        )
        with open(path, "r", encoding="utf-8") as f:
            src = f.read()
        self.assertIn("blacklist_access_token(payload)", src)
        self.assertNotIn("_token_blacklist.add(payload.get", src)


class TestCryptoFailClosed(unittest.TestCase):
    OLD_KEY = "chiave-vecchia-di-test"
    NEW_KEY = "chiave-nuova-di-test"

    def test_master_key_assente_solleva(self):
        with mock.patch.dict(os.environ, {"MASTER_KEY": ""}):
            with self.assertRaises(crypto_mod.CryptoConfigurationError):
                crypto_mod.validate_crypto_config()
            with self.assertRaises(crypto_mod.CryptoConfigurationError):
                crypto_mod.encrypt_value("segreto")

    def test_cryptography_assente_solleva_e_non_restituisce_plaintext(self):
        with mock.patch.dict(os.environ, {"MASTER_KEY": self.NEW_KEY}):
            with mock.patch.dict(
                sys.modules, {"cryptography": None, "cryptography.fernet": None}
            ):
                with self.assertRaises(crypto_mod.CryptoConfigurationError):
                    crypto_mod.validate_crypto_config()
                with self.assertRaises(crypto_mod.CryptoConfigurationError):
                    crypto_mod.encrypt_value("segreto")
                with self.assertRaises(crypto_mod.CryptoConfigurationError):
                    crypto_mod.decrypt_value("gAAAAA...")

    def test_roundtrip_e_chiave_diversa(self):
        with mock.patch.dict(os.environ, {"MASTER_KEY": self.NEW_KEY}):
            token = crypto_mod.encrypt_value("segreto-di-test")
            self.assertNotEqual(token, "segreto-di-test")
            self.assertTrue(token.startswith("gAAAAA"))
            self.assertEqual(crypto_mod.decrypt_value(token), "segreto-di-test")

        with mock.patch.dict(os.environ, {"MASTER_KEY": "altra-chiave"}):
            self.assertEqual(crypto_mod.decrypt_value(token), "")

    def test_migrazione_esplicita_con_chiave_legacy(self):
        with mock.patch.dict(os.environ, {"MASTER_KEY": self.OLD_KEY}):
            token = crypto_mod.encrypt_value("dato-storico")

        with mock.patch.dict(os.environ, {"MASTER_KEY": self.NEW_KEY}):
            self.assertEqual(crypto_mod.decrypt_value(token), "")
            self.assertEqual(
                crypto_mod.decrypt_with_legacy_key(token, self.OLD_KEY),
                "dato-storico",
            )

    def test_input_vuoto_resta_vuoto(self):
        with mock.patch.dict(os.environ, {"MASTER_KEY": self.NEW_KEY}):
            self.assertEqual(crypto_mod.encrypt_value(""), "")
            self.assertEqual(crypto_mod.decrypt_value(""), "")


if __name__ == "__main__":
    unittest.main()
