import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/main.css'
import './styles/tech-theme.css'
import './styles/phase-a.css'
import './styles/locked-tech-ui.css'
import App from './App.vue'
import router from './router'

createApp(App).use(createPinia()).use(router).use(ElementPlus).mount('#app')
