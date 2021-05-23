
# InTools: Interactive Tools

The goal of the InTools project is to develop interactive tools for various tasks.

The motivation is summarized in [Combining CLI and GUI](https://dundalek.com/entropic/combining-cli-and-gui/):

> The command-line interface (CLI) is a powerful way to control computers. However, it is also quite difficult to learn and more complicated to use. Graphical user interfaces (GUI) offer alternative approach that is more intuitive and easier to use but they loose some power. My goal is to explore an approach that takes power from CLI and intuitiveness from GUIs.

Another issue is that to this day software applications are still not very well integrated together.  Extension to functionality in cases when APIs are available requires serious engineering effort. As a result only most common use cases with the lowest common denominator are being invested in, while a long-tail of niche tasks remains underserved.

- One inspiring vision is outlined in [Magic Ink](http://worrydream.com/MagicInk/#engineering_inference_from_the_environment) by Bret Victor where modules can provide inference to accomplish novel tasks without programming them in.  
- Another source of ideas can be found in [Away with Applications](https://dundalek.com/entropic/enso-launcher/) by Aza Raskin describing a potential of a command-driven computing system.

Why are Terminal user interfaces (TUIs) an interesting choice for research and exploration?
- TUIs are on the edge between command line and graphical interfaces, therefore blending the best of both is a natural extension.
- Because visual representation of TUIs is constrained it lends itself to more focus on the underlying data model [fostering more creativity and innovation](https://hbr.org/2019/11/why-constraints-are-good-for-innovation). However, once good data models are in place, nothing prevents to build GUIs on top.
- Learnings about TUI possibilities will inform the design of the next-generation UI for the [closh shell](https://github.com/dundalek/closh).

The approach is to develop a variety of tools. Eventually common parts can be abstracted and evolved into a toolkit of of high-level primitives to allow building new tools even effortless. A good benchmark to strive for is to be able to create any interactive tool in not much more characters than it takes to write a manual page.

## Showcase

### [Spotin](./modules/spotin/): a client for Spotify

[add screenshot here]

See [documentation](./modules/spotin/README.md) for installation and usage instructions.

## License

MIT
