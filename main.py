from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.uix.button import Button
from kivy.uix.textinput import TextInput
from kivy.graphics import Color, Rectangle

class JarvisAssistant(BoxLayout):
    def __init__(self, **kwargs):
        super(JarvisAssistant, self).__init__(**kwargs)
        self.orientation = 'vertical'
        self.padding = 30
        self.spacing = 20

        # Dark theme background
        with self.canvas.before:
            Color(0.05, 0.05, 0.08, 1)
            self.rect = Rectangle(size=self.size, pos=self.pos)
        self.bind(size=self._update_rect, pos=self._update_rect)

        # Status Label
        self.status_label = Label(
            text="[color=00ff66][b]Jarvis: Ready & Stable[/b][/color]",
            markup=True,
            font_size='18sp',
            halign='center',
            valign='middle'
        )
        self.status_label.bind(size=self.status_label.setter('text_size'))
        self.add_widget(self.status_label)

        # Text Input
        self.user_input = TextInput(
            text='',
            hint_text='Yahan apna sawal likhiye...',
            size_hint=(1, 0.25),
            font_size='16sp',
            multiline=False
        )
        self.add_widget(self.user_input)

        # Action Button
        self.action_btn = Button(
            text="Jawab Dekhein",
            font_size='18sp',
            size_hint=(1, 0.25),
            background_color=(0.1, 0.5, 0.8, 1)
        )
        self.action_btn.bind(on_press=self.on_button_click)
        self.add_widget(self.action_btn)

    def _update_rect(self, instance, value):
        self.rect.pos = instance.pos
        self.rect.size = instance.size

    def on_button_click(self, instance):
        query = self.user_input.text.strip()
        if not query:
            self.status_label.text = "[color=ff3333][b]Pehle kuch toh likhiye bhai![/b][/color]"
            return
        
        self.status_label.text = f"[color=00f0ff][b]Aapne pucha: {query}[/b][/color]"


class TestApp(App):
    def build(self):
        return JarvisAssistant()


if __name__ == '__main__':
    TestApp().run()
    
