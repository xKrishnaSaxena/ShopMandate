"""Mock store-connect / OTP (§9.4) — mirrors the real Zepto/Swiggy OAuth-OTP shape."""

from __future__ import annotations


def start_otp(phone: str) -> str:
    digits = "".join(ch for ch in phone if ch.isdigit())[-10:]
    tail = digits[-2:] if len(digits) >= 2 else "21"
    return f"+91 {digits[:2] or '98'}XXX-XX{tail}"


def verify(otp: str) -> bool:
    otp = otp.strip()
    return otp == "123456" or (otp.isdigit() and len(otp) == 6)
