export interface LoginRequest {
    username: string;
    password: string;
}

export interface LoginResponse {
    accessToken: string;
    tokenType: string;
    expiresIn: number;
    expiresAt: string;
    username: string;
    roles: string[];
}

export interface CurrentUser {
    username: string;
    roles: string[];
    authenticated: boolean;
}
