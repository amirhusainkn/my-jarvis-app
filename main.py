from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.uix.button import Button
from kivy.clock import Clock

class JarvisBaseApp(App):
    def build(self):
        layout = BoxLayout(orientation='vertical', padding=30, spacing=20)
        
        self.status_label = Label(
            text="Jarvis Ready Hai!\nSir, main background mein tayyar hoon.",
            font_size='22sp',
            halign='center'
        )
        
        btn = Button(
            text="Test Speech / Sound",
            size_hint=(1, 0.25),
            background_color=(0, 0.7, 1, 1)
        )
        btn.bind(on_press=self.test_voice)
        
        layout.add_widget(self.status_label)
        layout.add_widget(btn)
        return layout

    def test_voice(self, instance):
        self.status_label.text = "Jarvis Active: Listening Mode ON!"

if __name__ == '__main__':
        JarvisBaseApp().run()
