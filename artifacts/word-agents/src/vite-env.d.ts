/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Origin of the Spring Boot API, e.g. https://api.example.com. Unset means same origin. */
  readonly VITE_API_URL?: string;
}
