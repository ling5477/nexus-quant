import type {ReactNode} from 'react';

export interface AppNavItem {
    key: string;
    path: string;
    label: string;
    icon: ReactNode;
    title: string;
    description: string;
    section?: string;
}

export interface RouteHandle {
    title: string;
    breadcrumb: string;
    menuKey?: string;
}
