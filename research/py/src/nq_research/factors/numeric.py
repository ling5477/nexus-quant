"""研究数值上下文不能继承 notebook 或调用方的精度、舍入和异常开关。"""

from decimal import ROUND_HALF_EVEN, Context, DivisionByZero, InvalidOperation, Overflow


def numeric_context() -> Context:
    return Context(prec=40, rounding=ROUND_HALF_EVEN, Emin=-999999, Emax=999999,
                   capitals=1, clamp=0, flags=[], traps=[InvalidOperation, DivisionByZero, Overflow])
