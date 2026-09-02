from datetime import datetime
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

        # Status Label (Sirf status dikhane ke liye)
        self.status_label = Label(
            text="[color=00ff66][b]Jarvis: Gemini Brain Loading Mode[/b][/color]",
            markup=True,
            font_size='18sp',
            halign='center',
            valign='middle'
        )
        self.add_widget(self.status_label)

        # Text Input (Sawal type karne ke liye)
        self.user_input = TextInput(
            text='',
            hint_text='Aamir bhai, yahan apna sawal likhiye...',
            size_hint=(1, 0.25),
            font_size='16sp',
            multiline=False
        )
        self.add_widget(self.user_input)

        # Action Button
        self.action_btn = Button(
            text="Gemini se Poochhein",
            font_size='18sp',
            size_hint=(1, 0.25),
            background_color=(0.1, 0.5, 0.8, 1)
        )
        self.action_btn.bind(on_press=self.ask_gemini_brain)
        self.add_widget(self.action_btn)

    def _update_rect(self, instance, value):
        self.rect.pos = instance.pos
        self.rect.size = instance.size

    def ask_gemini_brain(self, instance):
        query = self.user_input.text.strip()
        if not query:
            self.status_label.text = "[color=ff3333][b]Pehle kuch likhiye toh sahi, Aamir bhai![/b][/color]"
            return

        # Yahan hum process dikha rahe hain
        self.status_label.text = f"[color=00f0ff][b]Sawal: {query}\nGemini dimag se connect ho raha hai...[/b][/color]"
        
        
        # Agle step mein hum yahan apni Gemini API key aur request code dalenge
        # Abhi ke liye yeh base taiyar hai
        response_text = f"Aamir bhai, Gemini ka dimag is dabbe mein fit ho raha hai. Aapka sawal mil gaya hai!"

        # Screen par jawab dikhana
        self.status_label.text = f"[color=00ff66][b]{response_text}[/b][/color]"


class TestApp(App):
    def build(self):
        return JarvisAssistant()


if __name__ == '__main__':
    TestApp().run()
    
