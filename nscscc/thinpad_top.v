`default_nettype wire

module thinpad_top(
    input wire clk_50M,           //50MHz 时钟输入
    input wire clk_11M0592,       //11.0592MHz 时钟输入

    input wire clock_btn,         //BTN5手动时钟按钮开关，带消抖电路，按下时为1
    input wire reset_btn,         //BTN6手动复位按钮开关，带消抖电路，按下时为1

    input  wire[3:0]  touch_btn,  //BTN1~BTN4，按钮开关，按下时为1
    input  wire[31:0] dip_sw,     //32位拨码开关，拨到"ON"时为1
    output wire[15:0] leds,       //16位LED，输出时1点亮
    output wire[7:0]  dpy0,       //数码管低位信号，包括小数点，输出1点亮
    output wire[7:0]  dpy1,       //数码管高位信号，包括小数点，输出1点亮

    //CPLD串口控制器信号
    output wire uart_rdn,         //读串口信号，低有效
    output wire uart_wrn,         //写串口信号，低有效
    input wire uart_dataready,    //串口数据准备好
    input wire uart_tbre,         //发送数据标志
    input wire uart_tsre,         //数据发送完毕标志

    //BaseRAM信号
    inout wire[31:0] base_ram_data,  //BaseRAM数据，低8位与CPLD串口控制器共享
    output wire[19:0] base_ram_addr, //BaseRAM地址
    output wire[3:0] base_ram_be_n,  //BaseRAM字节使能，低有效。如果不使用字节使能，请保持为0
    output wire base_ram_ce_n,       //BaseRAM片选，低有效
    output wire base_ram_oe_n,       //BaseRAM读使能，低有效
    output wire base_ram_we_n,       //BaseRAM写使能，低有效

    //ExtRAM信号
    inout wire[31:0] ext_ram_data,  //ExtRAM数据
    output wire[19:0] ext_ram_addr, //ExtRAM地址
    output wire[3:0] ext_ram_be_n,  //ExtRAM字节使能，低有效。如果不使用字节使能，请保持为0
    output wire ext_ram_ce_n,       //ExtRAM片选，低有效
    output wire ext_ram_oe_n,       //ExtRAM读使能，低有效
    output wire ext_ram_we_n,       //ExtRAM写使能，低有效

    //直连串口信号
    output wire txd,  //直连串口发送端
    input  wire rxd,  //直连串口接收端

    //Flash存储器信号，参考 JS28F640 芯片手册
    output wire [22:0]flash_a,      //Flash地址，a0仅在8bit模式有效，16bit模式无意义
    inout  wire [15:0]flash_d,      //Flash数据
    output wire flash_rp_n,         //Flash复位信号，低有效
    output wire flash_vpen,         //Flash写保护信号，低电平时不能擦除、烧写
    output wire flash_ce_n,         //Flash片选信号，低有效
    output wire flash_oe_n,         //Flash读使能信号，低有效
    output wire flash_we_n,         //Flash写使能信号，低有效
    output wire flash_byte_n,       //Flash 8bit模式选择，低有效。在使用flash的16位模式时请设为1

    //图像输出信号
    output wire[2:0] video_red,    //红色像素，3位
    output wire[2:0] video_green,  //绿色像素，3位
    output wire[1:0] video_blue,   //蓝色像素，2位
    output wire video_hsync,       //行同步（水平同步）信号
    output wire video_vsync,       //场同步（垂直同步）信号
    output wire video_clk,         //像素时钟输出
    output wire video_de           //行数据有效信号，用于区分消隐区
);

//assign leds = dip_sw[15:0];

wire [31:0] io_isram_dout;
wire [19:0] io_isram_addr;
wire [31:0] io_isram_din;
wire        io_isram_en;
wire        io_isram_re;
wire        io_isram_we;
wire [3:0]  io_isram_wmask;

ram_wrapper iwrapper(
    .ram_data       (base_ram_data),
    .ram_addr       (base_ram_addr),
    .ram_be_n       (base_ram_be_n),
    .ram_ce_n       (base_ram_ce_n),
    .ram_oe_n       (base_ram_oe_n),
    .ram_we_n       (base_ram_we_n),

    .io_sram_dout   (io_isram_dout),
    .io_sram_addr   (io_isram_addr),
    .io_sram_din    (io_isram_din),
    .io_sram_en     (io_isram_en),
    .io_sram_re     (io_isram_re),
    .io_sram_we     (io_isram_we),
    .io_sram_wmask  (io_isram_wmask)
);

wire [31:0] io_dsram_dout;
wire [19:0] io_dsram_addr;
wire [31:0] io_dsram_din;
wire        io_dsram_en;
wire        io_dsram_we;
wire        io_dsram_re;
wire [3:0]  io_dsram_wmask;

ram_wrapper dwrapper(
    .ram_data       (ext_ram_data),
    .ram_addr       (ext_ram_addr),
    .ram_be_n       (ext_ram_be_n),
    .ram_ce_n       (ext_ram_ce_n),
    .ram_oe_n       (ext_ram_oe_n),
    .ram_we_n       (ext_ram_we_n),

    .io_sram_dout   (io_dsram_dout),
    .io_sram_addr   (io_dsram_addr),
    .io_sram_din    (io_dsram_din),
    .io_sram_en     (io_dsram_en),
    .io_sram_re     (io_dsram_re),
    .io_sram_we     (io_dsram_we),
    .io_sram_wmask  (io_dsram_wmask)

);

wire         io_uart_ar_ready;
wire  [7:0]  io_uart_r_id;
wire  [1:0]  io_uart_r_resp;
wire  [31:0] io_uart_r_data;
wire         io_uart_r_last;
wire         io_uart_r_valid;
wire         io_uart_aw_ready;
wire         io_uart_w_ready;
wire  [7:0]  io_uart_b_id;
wire  [1:0]  io_uart_b_resp;
wire         io_uart_b_valid;


wire [7:0]  io_uart_ar_id;
wire [31:0] io_uart_ar_addr;
wire [7:0]  io_uart_ar_len;
wire [2:0]  io_uart_ar_size;
wire [1:0]  io_uart_ar_burst;
wire        io_uart_ar_valid;
wire        io_uart_r_ready;
wire [7:0]  io_uart_aw_id;
wire [31:0] io_uart_aw_addr;
wire [7:0]  io_uart_aw_len;
wire [2:0]  io_uart_aw_size;
wire [1:0]  io_uart_aw_burst;
wire        io_uart_aw_valid;
wire [31:0] io_uart_w_data;
wire [3:0]  io_uart_w_strb;
wire        io_uart_w_last,
            io_uart_w_valid,
            io_uart_b_ready;


wire clk_cpu;
wire clk_locked;
reg rst_cpu;

clk_wiz_0 clk_wiz_0_inst(
    .reset(reset_btn),
    .clk_50M(clk_50M),
    .clk_cpu(clk_cpu),
    .locked(clk_locked)
);

always @(posedge clk_cpu or negedge clk_locked) begin
    if (~clk_locked) rst_cpu <= 1'b1;
    else rst_cpu <= 1'b0;
end

CoreNSCSCC core(
    .clock(clk_cpu),
    .reset(rst_cpu),
    .io_isram_dout(io_isram_dout),
    .io_dsram_dout(io_dsram_dout),
    .io_isram_addr(io_isram_addr),
    .io_isram_din(io_isram_din),
    .io_isram_en(io_isram_en),
    .io_isram_re(io_isram_re),
    .io_isram_we(io_isram_we),
    .io_isram_wmask(io_isram_wmask),
    .io_dsram_addr(io_dsram_addr),
    .io_dsram_din(io_dsram_din),
    .io_dsram_en(io_dsram_en),
    .io_dsram_re(io_dsram_re),
    .io_dsram_we(io_dsram_we),
    .io_dsram_wmask(io_dsram_wmask),

    .io_uart_ar_ready(io_uart_ar_ready),
    .io_uart_r_bits_id(io_uart_r_id),
    .io_uart_r_bits_resp(io_uart_r_resp),
    .io_uart_r_bits_data(io_uart_r_data),
    .io_uart_r_bits_last(io_uart_r_last),
    .io_uart_r_valid(io_uart_r_valid),
    .io_uart_aw_ready(io_uart_aw_ready),
    .io_uart_w_ready(io_uart_w_ready),
    .io_uart_b_bits_id(io_uart_b_id),
    .io_uart_b_bits_resp(io_uart_b_resp),
    .io_uart_b_valid(io_uart_b_valid),

    .io_uart_ar_bits_id(io_uart_ar_id),
    .io_uart_ar_bits_addr(io_uart_ar_addr),
    .io_uart_ar_bits_len(io_uart_ar_len),
    .io_uart_ar_bits_size(io_uart_ar_size),
    .io_uart_ar_bits_burst(io_uart_ar_burst),
    .io_uart_ar_valid(io_uart_ar_valid),
    .io_uart_r_ready(io_uart_r_ready),
    .io_uart_aw_bits_id(io_uart_aw_id),
    .io_uart_aw_bits_addr(io_uart_aw_addr),
    .io_uart_aw_bits_len(io_uart_aw_len),
    .io_uart_aw_bits_size(io_uart_aw_size),
    .io_uart_aw_bits_burst(io_uart_aw_burst),
    .io_uart_aw_valid(io_uart_aw_valid),
    .io_uart_w_bits_data(io_uart_w_data),
    .io_uart_w_bits_strb(io_uart_w_strb),
    .io_uart_w_bits_last(io_uart_w_last),
    .io_uart_w_valid(io_uart_w_valid),
    .io_uart_b_ready(io_uart_b_ready)
);

uart_wrapper#(
    .clk_freq(162000000),
    .uart_baud(9600)
) uart(
    .clk(clk_cpu),
    .rst(reset_btn),

    .txd(txd),
    .rxd(rxd),

    .io_uart_ar_ready(io_uart_ar_ready),
    .io_uart_r_id(io_uart_r_id),
    .io_uart_r_resp(io_uart_r_resp),
    .io_uart_r_data(io_uart_r_data),
    .io_uart_r_last(io_uart_r_last),
    .io_uart_r_valid(io_uart_r_valid),
    .io_uart_aw_ready(io_uart_aw_ready),
    .io_uart_w_ready(io_uart_w_ready),
    .io_uart_b_id(io_uart_b_id),
    .io_uart_b_resp(io_uart_b_resp),
    .io_uart_b_valid(io_uart_b_valid),

    .io_uart_ar_id(io_uart_ar_id),
    .io_uart_ar_addr(io_uart_ar_addr),
    .io_uart_ar_len(io_uart_ar_len),
    .io_uart_ar_size(io_uart_ar_size),
    .io_uart_ar_burst(io_uart_ar_burst),
    .io_uart_ar_valid(io_uart_ar_valid),
    .io_uart_r_ready(io_uart_r_ready),
    .io_uart_aw_id(io_uart_aw_id),
    .io_uart_aw_addr(io_uart_aw_addr),
    .io_uart_aw_len(io_uart_aw_len),
    .io_uart_aw_size(io_uart_aw_size),
    .io_uart_aw_burst(io_uart_aw_burst),
    .io_uart_aw_valid(io_uart_aw_valid),
    .io_uart_w_data(io_uart_w_data),
    .io_uart_w_strb(io_uart_w_strb),
    .io_uart_w_last(io_uart_w_last),
    .io_uart_w_valid(io_uart_w_valid),
    .io_uart_b_ready(io_uart_b_ready)
);

assign leds = dip_sw[15:0];

endmodule
