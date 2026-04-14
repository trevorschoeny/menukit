Divider — design doc                                                                                                                                     
                                                                                                                                                           
  Element purpose. A horizontal or vertical line separating content sections within a panel. Pure visual, no interaction, no state. The "visual separator"
  primitive.                                                                                                                                               
                                                                                            
  Works in all three contexts. Render-only; no input consequence.                                                                                          
                                                                                                                                                           
  ---                                                                                                                                                      
  Conventions pressure-test
                                                                                                                                                           
  Divider is the second Phase 8 element. Every convention from Icon applies here or is deliberately excepted.
                                                                                                                                                           
  Convention 1 — Constructor/factory shape (childX, childY, [width, height,] content, [callback])
                                                                                                                                                           
  Divider doesn't fit the pattern directly. It has no "content" in the Icon/TextLabel sense — only a direction, a length, and rendering configuration
  (color, thickness). The shape adapts:                                                                                                                    
                                                                                                                                                           
  Divider.horizontal(childX, childY, length);                                                                                                              
  Divider.horizontal(childX, childY, length, color, thickness);                                                                                            
  Divider.vertical(childX, childY, length);                                              
  Divider.vertical(childX, childY, length, color, thickness);                                                                                              
                                                                                         
  Reading order holds: where (childX, childY), how big (length), how it looks (color, thickness). The "where/how big/what" shape generalizes by treating
  "what" as baked into the factory choice (horizontal vs. vertical) rather than as a constructor parameter.                                                
                            
  Convention 2 — Supplier variants for variable content                                                                                                    
                                                                                                                                                           
  Does not apply. Divider has no variable content. Length, color, thickness, direction — all configuration, not element state. Per the
  data-vs-configuration distinction locked in Icon's review, configuration is fixed at construction; Divider has only configuration. No supplier forms.    
                                                                                         
  This is a clean "convention doesn't apply" case — good test that the data-vs-configuration refinement is actually doing work.
                                                                                                                                                           
  Convention 3 — Render-only inherits defaults
                                                                                                                                                           
  Applies. Divider overrides only the positional accessors, getWidth, getHeight, and render. Inherits isVisible (default true), mouseClicked (default      
  false), isHovered (default using own bounds — irrelevant for a non-interactive element but the default is safe).
                                                                                                                                                           
  Convention 4 — One builder method per element                                          
                                                                                                                                                           
  Adapts. Convention 4's rule is "one method name per element type." Divider has two factory methods (horizontal, vertical). The builder mirrors the
  factories:                                                                                                                                               
                                                                                         
  panelBuilder.horizontalDivider(x, y, length);
  panelBuilder.horizontalDivider(x, y, length, color, thickness);                                                                                          
  panelBuilder.verticalDivider(x, y, length);
  panelBuilder.verticalDivider(x, y, length, color, thickness);                                                                                            
                                                                                         
  Four builder methods for Divider (two default + two explicit) × two directions. Refining Convention 4: one method name per element type, plus 
  factory-matching method pair when the element ships direction factories. Only Divider has direction factories; no other planned element does.
                                                                                                                                                           
  Builder method count check. 3 existing (button, text, element) + Icon's 2 + Divider's 4 = 9 after Divider. Still on the comfortable side of the 16-method
   crowding threshold. Phase 8 will add ~4-6 more methods (toggle, checkbox, itemDisplay, progressBar, tooltip — some with one overload, some with two).   
  Final count ~15. Right at the crowded threshold. Acceptable but worth noticing — Phase 9 additions should resist the temptation to pile on more.
                                                                                                                                                           
  Convention 5 — No factory methods except direction cases                                                                                                 
                          
  First applicable case. Divider uses factories precisely because horizontal/vertical is the canonical "meaningless enum" case Convention 5 names. Without 
  factories, consumers would write new Divider(x, y, length, Direction.HORIZONTAL) — two call sites with a discriminator enum is worse than two factories
  with the discriminator baked in.                                                                                                                         
   
  Green-light the convention as stated. Divider is the one case; no other Phase 8 element needs factories.                                                 
                                                                                         
  Convention 6 — Vanilla textures for MenuKit defaults                                                                                                     
                                                                                         
  First material application. Divider needs a default visual. Options:                                                                                     
                          
  - (a) Solid-color fill via graphics.fill(). No texture involved. Color chosen to match vanilla's visible separator aesthetic — typically the             
  inventory-label dark gray 0xFF404040, or a slightly lighter inset-style gray.          
  - (b) Vanilla sprite tiled horizontally/vertically. Requires finding a vanilla sprite that looks like a line. None of vanilla's HUD sprites are pure 1px 
  line primitives; the closest analogues are edge pixels of larger sprites.              
  - (c) Custom sprite shipped in MenuKit. A 1×1 or 2×2 pixel custom texture.                                                                               
                                                                                         
  Choice: (a) solid-color fill. Reasoning:                                                                                                                 
                                                                                                                                                           
  1. Convention 6 prefers vanilla textures where a texture is needed. A divider doesn't need a texture — a solid color fill is both simpler and correct.
  Zero-texture rendering is not a Convention 6 violation; it's orthogonal to the vanilla-vs-custom axis.                                                   
  2. graphics.fill() goes through the same pipeline vanilla uses for its own solid-color overlays (the darkness overlay when a screen is open, for         
  example). Resource packs don't affect solid-color rendering, so there's nothing to adapt — but there's also no custom asset to maintain.                 
  3. The resulting color will be consistent across resource packs (solid colors don't re-texture), matching vanilla's behavior for its own fills. Consumers
   who want textured dividers implement PanelElement directly with their own sprite.     
                                                                                                                                                           
  Default color choice. 0xFF404040 (vanilla inventory-label dark gray). Visible against light panel backgrounds, matches the existing vanilla visual       
  vocabulary. Consumers can override via the explicit-parameter factory.
                                                                                                                                                           
  Default thickness. 1 pixel. Explicit factory allows override.                          
                                                                                                                                                           
  ---                                                                                    
  API                                                                                                                                                      
                                                                                                                                                           
  public class Divider implements PanelElement {
                                                                                                                                                           
      /** Default separator color — vanilla inventory-label dark gray. */                
      public static final int DEFAULT_COLOR = 0xFF404040;
                                         
      /** Default thickness in pixels. */                                                                                                                  
      public static final int DEFAULT_THICKNESS = 1;
                                                                                                                                                           
      public static Divider horizontal(int childX, int childY, int length);                                                                                
      public static Divider horizontal(int childX, int childY, int length, int color, int thickness);
      public static Divider vertical(int childX, int childY, int length);                                                                                  
      public static Divider vertical(int childX, int childY, int length, int color, int thickness);
                                                                                                                                                           
      // PanelElement                    
      public int getChildX();                                                                                                                              
      public int getChildY();                                                            
      public int getWidth();                                                                                                                               
      public int getHeight();                                                                                                                              
      public void render(RenderContext ctx);
  }                                                                                                                                                        
                                                                                         
  Internal representation: a single class with width, height, and color fields. The factory methods set width/height per direction — horizontal produces
  (length, thickness), vertical produces (thickness, length). Private constructor; consumers always use factories.                                         
                            
  Render implementation:                                                                                                                                   
  @Override                                                                                                                                                
  public void render(RenderContext ctx) {
      int x = ctx.originX() + childX;                                                                                                                      
      int y = ctx.originY() + childY;                                                    
      ctx.graphics().fill(x, y, x + width, y + height, color);
  }                                                           
                            
  Builder additions:
  public PanelBuilder horizontalDivider(int childX, int childY, int length);                                                                               
  public PanelBuilder horizontalDivider(int childX, int childY, int length, int color, int thickness);
  public PanelBuilder verticalDivider(int childX, int childY, int length);                                                                                 
  public PanelBuilder verticalDivider(int childX, int childY, int length, int color, int thickness);
                                                                                                                                                           
  ---                                    
  Scope boundary — what Divider does not do                                                                                                                
                                                                                                                                                           
  - No gradient, pattern, or textured rendering. Solid color only. Consumers wanting fancier separators implement PanelElement directly.
  - No automatic length. Divider doesn't introspect its containing panel to auto-size. Length is explicit. Consumers who want "full-width divider" compute 
  the panel's content width themselves (it's known at construction time — it's the panel's intended width).
  - No rounded ends, caps, or styling. Rectangle only.                                                                                                     
  - No inset-style double-line variant (a horizontal light line above a dark line, like vanilla panel separators). Phase 9 at earliest if consumer demand
  surfaces; unlikely to be needed because PanelStyle.INSET already handles this case at the panel level.                                                   
                                                                                                                                                           
  ---                                                                                                                                                      
  Phase 9 and beyond                                                                                                                                       
                                                                                                                                                           
  Nothing flagged. Divider is terminal — no composition with other elements, no Phase 9 specialization expected. If demand surfaces for styled dividers    
  (gradient, double-line, custom texture), they'll be audit-driven additions rather than Phase 8 conventions.                                              
                                                                                         
  ---                       
  Refinements to conventions (roll forward to subsequent elements)
                                                                                                                                                           
  Two small refinements surfaced through Divider's pressure test:
                                                                                                                                                           
  Convention 4 refined — "one method name per element type, plus factory-matching method pair when the element ships direction factories." Only Divider
  exercises the exception.                                                                                                                                 
                            
  Convention 6 refined — solid-color fills via graphics.fill() are not a texture-choice decision; they're a no-texture decision and don't conflict with the
   vanilla-texture preference. The preference governs texture choice; it doesn't mandate a texture where a fill is sufficient.                             
                                                                                                                                                           
  Both refinements narrow rather than expand the conventions. Subsequent elements inherit them.                                                            
                                                                                                                                                           
  ---                       
  Summary                                                                                                                                                  
                                                                                                                                                           
  Divider confirms four conventions (1, 3, 5, 6) and refines two (4, 6). Convention 2 doesn't apply (no variable content) — a clean "inapplicable" case
  that verifies the data-vs-configuration distinction works.                                                                                               
                                                                                         
  Small file (~60 lines). ~4 builder method additions. Solid-color default visual; no textures shipped; vanilla-matching color. Ready for implementation
  after review.           
                     