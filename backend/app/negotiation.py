"""Compare two stores + one light haggle round (§9.3)."""

from __future__ import annotations

from dataclasses import dataclass, field

from .models import Cart, Intent, Quote, Winner


@dataclass
class Decision:
    status: str  # "awaiting_approval" | "over_budget" | "no_stock"
    quotes: list[Quote] = field(default_factory=list)
    winner: Winner | None = None
    cart: Cart | None = None
    steps: list[str] = field(default_factory=list)  # animatable negotiation log
    clarifying_question: str | None = None


def decide(intent: Intent, quotes: list[Quote]) -> Decision:
    if not quotes:
        return Decision(status="no_stock", steps=["Kisi bhi store me stock nahi mila 😕"])
    ordered = sorted(quotes, key=lambda q: q.price_inr)
    if len(ordered) == 1:
        only = ordered[0]
        return _finalize(intent, only, ordered, other=None,
                         steps=[f"Sirf {only.store} ke paas hai — {only.store} chuna."])

    winner, runner_up = ordered[0], ordered[1]
    listing = " · ".join(f"{q.store}: ₹{q.price_inr} ({q.delivery})" for q in ordered)
    steps = [
        listing,
        f"{winner.store} sabse sasta — {runner_up.store} se match maanga…",
    ]
    # one counter round: runner-up drops partway but not below the winner
    countered = max(winner.price_inr + 50, runner_up.price_inr - 100)
    steps.append(f"{runner_up.store} ne ₹{countered} tak kiya — phir bhi {winner.store} jeeta.")
    return _finalize(intent, winner, ordered, other=runner_up, steps=steps)


def _finalize(intent: Intent, winner: Quote, quotes: list[Quote], other: Quote | None,
              steps: list[str]) -> Decision:
    # budget guard
    if intent.budget_inr is not None and winner.price_inr > intent.budget_inr:
        return Decision(
            status="over_budget", quotes=quotes, steps=steps,
            clarifying_question=(
                f"Sabse sasta ₹{winner.price_inr} hai, tera budget ₹{intent.budget_inr} — "
                "thoda badhau ya kuch aur dekhu?"
            ),
        )
    gap = (other.price_inr - winner.price_inr) if other else 0
    why = (f"₹{gap} sasta + {winner.delivery} delivery" if gap > 0
           else f"{winner.delivery} delivery, in stock")
    cart = Cart(item=winner.product_name, qty=intent.qty, price_inr=winner.price_inr,
                store=winner.store, delivery="kal shaam tak")
    return Decision(
        status="awaiting_approval", quotes=quotes,
        winner=Winner(store=winner.store, price_inr=winner.price_inr, why=why),
        cart=cart, steps=steps,
    )
