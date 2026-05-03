import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import {
  Button,
  Cell,
  CellGroup,
  ConfigProvider,
  Field,
  Form,
  Icon,
  List,
  Picker,
  Popup,
  Radio,
  RadioGroup,
  Slider,
  Space,
  Stepper,
  Switch,
  Tabbar,
  TabbarItem,
  Tag,
  Toast,
  Uploader,
  Empty,
  SwipeCell,
  DropdownMenu,
  DropdownItem,
} from 'vant'
import 'vant/lib/index.css'
import './styles/main.css'

const app = createApp(App)

app
  .use(router)
  .use(Button)
  .use(Cell)
  .use(CellGroup)
  .use(ConfigProvider)
  .use(Field)
  .use(Form)
  .use(Icon)
  .use(List)
  .use(Picker)
  .use(Popup)
  .use(Radio)
  .use(RadioGroup)
  .use(Slider)
  .use(Space)
  .use(Stepper)
  .use(Switch)
  .use(Tabbar)
  .use(TabbarItem)
  .use(Tag)
  .use(Toast)
  .use(Uploader)
  .use(Empty)
  .use(SwipeCell)
  .use(DropdownMenu)
  .use(DropdownItem)

app.mount('#app')
