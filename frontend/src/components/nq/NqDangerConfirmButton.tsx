import {App, Button} from 'antd';
import type {ButtonProps} from 'antd';
import type {ReactNode} from 'react';

/**
 * NqDangerConfirmButton — 危险操作统一二次确认按钮。
 *
 * 适用：紧急停机、停止运行、删除等不可逆或高影响操作。
 * 关键约束：
 * 1) 确认弹窗必须说明影响面（confirmContent 由调用方提供业务口径）；
 * 2) 本组件只负责交互防误触，不承载任何权限/风控判断（后端仍是唯一校验点）。
 */
interface NqDangerConfirmButtonProps extends Omit<ButtonProps, 'onClick' | 'danger'> {
    confirmTitle: ReactNode;
    confirmContent: ReactNode;
    okText?: string;
    onConfirm: () => void;
    children: ReactNode;
}

export function NqDangerConfirmButton({
    confirmTitle,
    confirmContent,
    okText = '确认执行',
    onConfirm,
    children,
    ...buttonProps
}: NqDangerConfirmButtonProps) {
    const {modal} = App.useApp();

    return (
        <Button
            danger
            {...buttonProps}
            onClick={() => {
                modal.confirm({
                    title: confirmTitle,
                    content: confirmContent,
                    okText,
                    okButtonProps: {danger: true},
                    cancelText: '取消',
                    onOk: onConfirm,
                });
            }}
        >
            {children}
        </Button>
    );
}
