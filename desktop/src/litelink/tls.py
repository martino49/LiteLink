"""Self-signed TLS certificate management for WSS.

LiteLink uses WSS on the local network. On first run we generate a
self-signed ECDSA certificate. The companion app trusts it via SHA-256
fingerprint pinning (TOFU — trust on first use): it records the
fingerprint on the first successful pairing and verifies it afterwards.
"""

import ssl
from datetime import datetime, timedelta, timezone
from pathlib import Path

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.x509.oid import NameOID

CERT_NAME = "cert.pem"
KEY_NAME = "key.pem"


def ensure_certificate(data_dir: Path) -> tuple[Path, Path]:
    """Generate (or load) the self-signed certificate for WSS.

    Returns (cert_path, key_path).
    """
    cert_path = data_dir / CERT_NAME
    key_path = data_dir / KEY_NAME
    if cert_path.exists() and key_path.exists():
        return cert_path, key_path

    data_dir.mkdir(parents=True, exist_ok=True)

    key = ec.generate_private_key(ec.SECP256R1())
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "LiteLink")])
    now = datetime.now(timezone.utc)
    cert = (
        x509.CertificateBuilder()
        .subject_name(name)
        .issuer_name(name)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - timedelta(minutes=5))
        .not_valid_after(now + timedelta(days=3650))
        .add_extension(
            x509.SubjectAlternativeName([x509.DNSName("localhost")]),
            critical=False,
        )
        .sign(key, hashes.SHA256())
    )

    cert_path.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    key_path.write_bytes(
        key.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption(),
        )
    )
    return cert_path, key_path


def build_ssl_context(data_dir: Path) -> ssl.SSLContext:
    """Build an SSLContext for the WebSocket server."""
    cert_path, key_path = ensure_certificate(data_dir)
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ctx.load_cert_chain(cert_path, key_path)
    return ctx


def certificate_fingerprint(data_dir: Path) -> str:
    """SHA-256 fingerprint of the cert as lowercase hex (no separators)."""
    cert_path, _ = ensure_certificate(data_dir)
    cert = x509.load_pem_x509_certificate(cert_path.read_bytes())
    return cert.fingerprint(hashes.SHA256()).hex()


def format_fingerprint(hex_fp: str) -> str:
    """Render a hex fingerprint as colon-separated uppercase groups."""
    return ":".join(hex_fp[i:i + 2].upper() for i in range(0, len(hex_fp), 2))
